package io.github.darkantoine.protowrapper;

import static io.github.darkantoine.protowrapper.StringUtils.EMPTY_STRING;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.google.protobuf.BoolValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class WrapperBuilder {

  private String PREFIX = EMPTY_STRING;
  private String SUFFIX = EMPTY_STRING;
  private final Class<?> listClass = LinkedList.class;
  private final Class<?> mapClass = HashMap.class;
  
  static Map<Class<?>, Class<?>> WKTMap = new HashMap<Class<?>,Class<?>>();
  static {
    WKTMap.put(StringValue.class, String.class);
    WKTMap.put(Int32Value.class, Integer.class);
    WKTMap.put(Int64Value.class, Long.class);
    WKTMap.put(FloatValue.class, Float.class);
    WKTMap.put(DoubleValue.class, Double.class);
    WKTMap.put(BoolValue.class, Boolean.class);
  }
  
  protected Map<Class<?>, JavaFile.Builder> wrappedJavaFilesMap = new HashMap<Class<?>, JavaFile.Builder>();
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
  private Map<Class<?>, ClassParameters> classesParameters = new HashMap<Class<?>, ClassParameters>();
  private ClassParameters defaultParameters = new ClassParameters(null, new LinkedList<ClassVariable>(), true, false);
  
  private Class<?> currentlyProcessing = null;
  private String rootPackageName = "com.example";
  private Map<String, String> packagePatterns = new HashMap<String,String>();

  public WrapperBuilder() {
  }

  public WrapperBuilder(Class<?> abstractClass, String prefix, String suffix) {
    if (ClassUtils.isAbstract(abstractClass)) {
      this.abstractClass = abstractClass;
    } else {
      throw new IllegalArgumentException(abstractClass.getCanonicalName() + " is not an abstractClass");
    }
    PREFIX= prefix;
    SUFFIX= suffix;    
  }
  
  public WrapperBuilder(Class<?> abstractClass) {
    this(abstractClass, EMPTY_STRING,EMPTY_STRING);    
  }
  
  public void addClass(Class<?> protoClass) {
    addClass(protoClass,null);
  }

  public void addClass(Class<?> protoClass, ClassParameters classParameters) {
    protoLoaders.put(protoClass, new ProtoLoader(protoClass));
    classNameMap.put(protoClass, computeWrappedClassName(protoClass));
    if(classParameters !=null) {
      classesParameters.put(protoClass, classParameters);
    }
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

  protected void build(Class<?> protoClass) {
    ClassBuilder classBuilder = new ClassBuilder (protoClass, this);
    wrappedJavaFilesMap.put(protoClass, JavaFile.builder(getPackageName(protoClass), classBuilder.build()));
  }

  private static String computeUniqueClassName(Class<?> inputClass, List<Class<?>> collisions) {

    String className = inputClass.getCanonicalName();
    String[] classNameparts = className.split("\\.");
     
    StringBuilder sb = new StringBuilder();
    for(String s: classNameparts) {
      sb.append(StringUtils.capitalize(s));
    }
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
    if (com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(javaClass) && !javaClass.getCanonicalName().startsWith("com.google.protobuf")) {
      if (!currentlyProcessing.equals(javaClass) && !todo.contains(javaClass) && !wrappedJavaFilesMap.containsKey(javaClass)) {
        this.addClass(javaClass);
      }
      return ClassName.get(getPackageName(javaClass), classNameMap.get(javaClass));
    }
    if(WKTMap.containsKey(javaClass)) {
      return ClassName.get(WKTMap.get(javaClass));
    }
    return ClassName.get(javaClass);
  }

  protected String getPackageName(Class<?> javaClass) {
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
    return wrappedJavaFilesMap.get(clazz).build().toString();
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
    return wrappedJavaFilesMap.keySet();
  }

  public Class<?> getListClass() {
    return listClass;
  }

  public Class<?> getMapClass() {
    return mapClass;
  }
  
  public static class ClassParameters {
    Class<?> abstractClass;
    List<ClassVariable> classVariables;
    boolean isPassedToChildClass;
    boolean isRootClass;
    
    public ClassParameters(Class<?> abstractClass, List<ClassVariable> classVariables, boolean isPassedToChildClass, boolean isRootClass) {
      this.abstractClass = abstractClass;
      this.classVariables = classVariables;
      this.isPassedToChildClass = isPassedToChildClass;
      this.isRootClass = isRootClass;
    }

    public Class<?> getAbstractClass() {
      return abstractClass;
    }

    public List<ClassVariable> getClassVariables() {
      return classVariables;
    }

    public boolean isChildClass() {
      return isPassedToChildClass;
    }

    public boolean isRootClass() {
      return isRootClass;
    } 
    
  }
  
  public static class ClassVariable {
    private Class<?> variableClass;
    private String variableName;
    boolean addedToConstructor;
    boolean passedToConstructors;
    
   
    public ClassVariable(Class<?> variableClass, String variableName, boolean addedToConstructor,
        boolean passedToConstructors) {
      this.variableClass = variableClass;
      this.variableName = variableName;
      this.addedToConstructor = addedToConstructor;
      this.passedToConstructors = passedToConstructors;
    }

    public Class<?> getVariableClass() {
      return variableClass;
    }

    public String getVariableName() {
      return variableName;
    }

    public boolean isAddedToConstructor() {
      return addedToConstructor;
    }

    public boolean isPassedToConstructors() {
      return passedToConstructors;
    }    
    
  }

  public Map<Class<?>, ClassParameters> getClassesParameters() {
    return classesParameters;
  }

  public ClassParameters getDefaultParameters() {
    return defaultParameters;
  }

  public void setDefaultParameters(ClassParameters defaultParameters) {
    this.defaultParameters = defaultParameters;
  }
  
  
    
}
