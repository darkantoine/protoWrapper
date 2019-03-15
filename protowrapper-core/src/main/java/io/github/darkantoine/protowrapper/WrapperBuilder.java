package io.github.darkantoine.protowrapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class WrapperBuilder {

  private final String PREFIX = "";
  private final String SUFFIX = "Facade";
  private final Class<?> listClass = LinkedList.class;
  private final Class<?> mapClass = HashMap.class;
  private Map<Class<?>, JavaFile.Builder> units = new HashMap<Class<?>, JavaFile.Builder>();
  private Map<Class<?>, ProtoLoader> protoLoaders = new HashMap<Class<?>, ProtoLoader>();
  private Map<Class<?>, String> classNameMap = new HashMap<Class<?>, String>();
  public Map<Class<?>, ProtoLoader> getProtoLoaders() {
    return protoLoaders;
  }
  private Class<?> abstractClass;

  public Class<?> getAbstractClass() {
    return abstractClass;
  }

  public void setProtoLoaders(Map<Class<?>, ProtoLoader> protoLoaders) {
    this.protoLoaders = protoLoaders;
  }

  public Map<Class<?>, String> getClassNameMap() {
    return classNameMap;
  }

  private Set<Class<?>> todo = new HashSet<Class<?>>();
  private Class<?> currentlyProcessing = null;
  private String rootPackageName = "com.example";
  private Map<String, String> packagePatterns = new HashMap<String,String>();

  public WrapperBuilder() {
  }

  public WrapperBuilder(Class<?> abstractClass) {
    if (ClassUtils.isAbstract(abstractClass)) {
      this.abstractClass = abstractClass;
    } else {
      throw new IllegalArgumentException(abstractClass.getCanonicalName() + " is not an abstractClass");
    }
  }

  public void addClass(Class<?> protoClass) {
    protoLoaders.put(protoClass, new ProtoLoader(protoClass));
    classNameMap.put(protoClass, computeWrappedClassName(protoClass));
    todo.add(protoClass);
  }

  public void build() {
    while (!todo.isEmpty()) {
      Set<Class<?>> toProcess = new HashSet<Class<?>>(todo);
      toProcess.stream().forEach(this::process);
    }
  }

  private void process(Class<?> protoClass) {
    currentlyProcessing = protoClass;
    build(protoClass);
    todo.remove(currentlyProcessing);
  }

  private void build(Class<?> protoClass) {

    // System.out.println("processing: "+protoClass.getCanonicalName());
    ClassBuilder classBuilder = new ClassBuilder (protoClass, this);

    units.put(protoClass, JavaFile.builder(getPackageName(protoClass), classBuilder.build()));
  }

  private static String computeUniqueClassName(Class<?> inputClass, List<Class<?>> collisions) {

    String className = inputClass.getCanonicalName();
    String[] classNameparts = className.split("\\.");
    
    //List<String[]> collisionSplits = collisions.stream().map(Class<?>::getCanonicalName).map( x -> x.split("\\."))
        
    StringBuilder sb = new StringBuilder();
    for(String s: classNameparts) {
      sb.append(StringUtils.capitalize(s));
    }
    
    /*
    String pattern = "([a-zA-Z])([a-zA-Z0-9]+)(\\.)";
    Matcher m = Pattern.compile(pattern).matcher(className);

    StringBuilder sb = new StringBuilder();
    int last = 0;
    while (m.find()) {
      sb.append(m.group(1).toUpperCase());
      sb.append(m.group(2));
      last = m.end();
    }
    sb.append(className.substring(last));
    */
    return sb.toString();
  }



  TypeName computeReturnType(FieldDetails fieldDetails) {
    if (fieldDetails.isMapField()) {
      return ParameterizedTypeName.get(ClassName.get(Map.class), TypeName.get(fieldDetails.getMapKeyClass()),
          getWrappedTypeName(fieldDetails.getJavaClass()));
    } else if (fieldDetails.isRepeated()) {
      return ParameterizedTypeName.get(ClassName.get(List.class), getWrappedTypeName(fieldDetails.getJavaClass()));
    } else {
      return getWrappedTypeName(fieldDetails.getJavaClass());
    }
  }

  TypeName getWrappedTypeName(Class<?> javaClass) {
    // System.out.println(javaClass.toString() + " vs " + currentlyProcessing.toString());
    if (com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(javaClass) && !javaClass.getCanonicalName().startsWith("com.google.protobuf")) {
      if (!currentlyProcessing.equals(javaClass) && !todo.contains(javaClass) && !units.containsKey(javaClass)) {
        this.addClass(javaClass);
      }
      return ClassName.get(getPackageName(javaClass), classNameMap.get(javaClass));
    }
    return ClassName.get(javaClass);
  }

  private String getPackageName(Class<?> javaClass) {
    String fullClassName = javaClass.getCanonicalName();
    for(String pattern: packagePatterns.keySet()) {
      if(fullClassName.matches(pattern)){
        return rootPackageName+packagePatterns.get(pattern);
      }
    }
    return rootPackageName;
  }
  
  public boolean addPackagePattern(String pattern, String packageSuffix) {
    try{
      Pattern.compile(pattern);
    } catch(PatternSyntaxException e) {
      System.out.println(pattern+" is not a good pattern");
      return false;
    }
    String packageSuffixRegex = "(\\.[a-zA-Z][a-zA-Z0-9_]*)+";
    if(!packageSuffix.matches(packageSuffixRegex)) {
      System.out.println(packageSuffix+" is not a good suffix");
      return false;
    }
    return packagePatterns.put(pattern, packageSuffix)!=null;
  }

  public String getJavaFileForClass(Class<?> clazz) {
    return units.get(clazz).build().toString();
  }

  private String computeWrappedClassName(Class<?> clazz) {
    if (!ClassUtils.isProtocolBufferMessage(clazz)) {
      return clazz.getCanonicalName();
    }

    if (!classNameMap.containsKey(clazz)) {

      System.out.println("new class: " + clazz.getCanonicalName());
      List<Class<?>> collisions = classNameMap.keySet().stream()
          .filter(x -> x.getSimpleName().equals(clazz.getSimpleName())).collect(Collectors.toList());
      // collisions.stream().forEach(x -> System.out.println("EXISTING: " + x.getCanonicalName()));

      if (collisions.size() > 0) {
        // System.out.println("long name: "+PREFIX+classNameToCamelCase(clazz));
        classNameMap.put(clazz, PREFIX + computeUniqueClassName(clazz, collisions) + SUFFIX);
      } else {
        classNameMap.put(clazz, PREFIX + clazz.getSimpleName() + SUFFIX);
      }
      //System.out.println("with name: " + classNameMap.get(clazz));
    }
    return classNameMap.get(clazz);
  }

  public String getWrappedClassName(Class<?> clazz) {
    if (!classNameMap.containsKey(clazz)) {
      return clazz.getCanonicalName();
    }
    return classNameMap.get(clazz);
  }

  public String getPackageForClass(Class<?> clazz) {
    return getPackageName(clazz);
  }
  public Set<Class<?>> getGeneratedClasses() {
    return units.keySet();
  }

  public Class<?> getListClass() {
    return listClass;
  }

  public Class<?> getMapClass() {
    return mapClass;
  }
    
}
