package io.github.darkantoine.protowrapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class WrapperBuilder {
   
  private static final String PREFIX = "Wrapped";
  private Map<Class<?>,JavaFile.Builder> units = new HashMap<Class<?>,JavaFile.Builder>();
  private Map<Class<?>,ProtoLoader> protoLoaders = new HashMap<Class<?>,ProtoLoader>();
  private Map<Class<?>, String> classNameMap =  new HashMap<Class<?>,String>();
  private Set<Class<?>> todo = new HashSet<Class<?>>();
  private Class<?> currentlyProcessing = null;
  private String packageName = "com.example";
  
  public WrapperBuilder() {
  }
  
  public void addClass(Class<?> protoClass) {
    protoLoaders.put(protoClass, new ProtoLoader(protoClass));
    classNameMap.put(protoClass, getWrappedClassName(protoClass));
    todo.add(protoClass);
  }
  
  public void build() {
    
    while(!todo.isEmpty()) {
      Set<Class<?>> toProcess = new HashSet<Class<?>>(todo);
      toProcess.stream().forEach(this::process);
    }
    
  }
  
  private void process(Class<?> protoClass) {
    process(protoClass, protoLoaders.get(protoClass));
    }

  private void process(Class<?> protoClass, ProtoLoader protoLoader) {
    
    currentlyProcessing = protoClass;
    System.out.println("processing: "+protoClass.getCanonicalName());
    
    String className = classNameMap.get(protoClass);
    
     TypeSpec.Builder wrapperClassBuilder = TypeSpec.classBuilder(className)
            .addModifiers(javax.lang.model.element.Modifier.PUBLIC);

    String protoFieldName = lowerCamel(className)+"PB";
    wrapperClassBuilder.addField(protoClass, protoFieldName, Modifier.PRIVATE);

    addConstructor(wrapperClassBuilder,protoClass,protoLoader, protoFieldName);
    
    addMethods(wrapperClassBuilder,protoClass,protoLoader, protoFieldName);
        units.put(protoClass, JavaFile.builder(packageName,wrapperClassBuilder.build()));

    
    todo.remove(currentlyProcessing);
  }
  

  private void addConstructor(Builder wrapperClassBuilder, Class<?> protoClass, ProtoLoader protoLoader,
      String protoFieldName) {
    MethodSpec.Builder mb;
    mb = MethodSpec.constructorBuilder()
        .addParameter(protoClass, protoFieldName);
    mb.addModifiers(Modifier.PUBLIC);
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("this."+protoFieldName+"= "+protoFieldName);
    mb.addCode(builder.build());
    wrapperClassBuilder.addMethod(mb.build());
    
  }

  private static String classNameToCamelCase(Class<?> inputClass) {
    
    String className = inputClass.getCanonicalName();
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
    return sb.toString();
  }


  private void addMethods(Builder wrapperClassBuilder, Class<?> protoClass, ProtoLoader protoLoader, String protoFieldName) {
    protoLoader.getMethodsMap().values().stream().forEach(x -> this.addMethod(x, wrapperClassBuilder, protoFieldName));    
  }

  private void addMethod(FieldDetails fieldDetails, Builder wrapperClassBuilder, String protoFieldName) {
   
    TypeName typeName;
    if(fieldDetails.isMapField()) {
      typeName = ParameterizedTypeName.get(ClassName.get(Map.class), TypeName.get(fieldDetails.getMapKeyClass()),wrapped(fieldDetails.getJavaClass()));
    }
    else if (fieldDetails.isRepeated()) {        
      typeName = ParameterizedTypeName.get(ClassName.get(List.class), wrapped(fieldDetails.getJavaClass()));      
      }
    else {
      typeName =  wrapped(fieldDetails.getJavaClass());
    }
    MethodSpec.Builder mb;
    mb = MethodSpec.methodBuilder(getterNameForField(fieldDetails)).returns(typeName);
    mb.addModifiers(Modifier.PUBLIC);
    mb.addCode(codeBlockForField(fieldDetails, protoFieldName));
    wrapperClassBuilder.addMethod(mb.build());

  }

  private CodeBlock codeBlockForField(FieldDetails fieldDetails, String protoFieldName) {
    StringBuilder sb = new StringBuilder();
    CodeBlock.Builder builder = CodeBlock.builder();
    if (JavaType.MESSAGE.equals(fieldDetails.getJavaType())) {
      if (!fieldDetails.isRepeated()) {
        sb.append("return new $T");       
        sb.append("(" + protoFieldName);
        sb.append("." + getterNameForField(fieldDetails) + "())");
        builder.addStatement(sb.toString(), wrapped(fieldDetails.getJavaClass()));
      } else {
        if(fieldDetails.isMapField()) {
          if(com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(fieldDetails.getJavaClass())) {
            
            TypeName classType = wrapped(fieldDetails.getJavaClass());
            TypeName keyClass = wrapped(fieldDetails.getMapKeyClass());
            //Using HashMap for now. TODO: make this configurable
            builder.addStatement("$T<$T,$T> map = new $T<$T,$T>()",Map.class, keyClass,classType,HashMap.class, keyClass,classType);
            builder.beginControlFlow("for($T item: "+protoFieldName+"."+ getterNameForField(fieldDetails) +"().keySet())",keyClass)
            .addStatement("map.put(item, new $T("+protoFieldName+"."+ getterNameForField(fieldDetails) +"().get(item)))",classType)
            .endControlFlow();
            builder.addStatement("return map");  
          }
          else {
            sb.append("return ");
            sb.append(protoFieldName);
            sb.append("." + getterNameForField(fieldDetails) + "()");
            builder.addStatement(sb.toString());
          }
          
        } else {
          
          TypeName classType = wrapped(fieldDetails.getJavaClass());
          //Using LinkedList for now. TODO: make this configurable
          builder.addStatement("$T<$T> list = new $T<$T>()",List.class,classType,LinkedList.class,classType);
          builder.beginControlFlow("for($T item:"+protoFieldName+"."+ getterNameForField(fieldDetails) +"())",fieldDetails.getJavaClass())
          .addStatement("list.add(new $T(item))",classType)
          .endControlFlow();
          builder.addStatement("return list");  
          
        }
      }
      return builder.build();
    } else {
      sb.append("return ");
      sb.append(protoFieldName);
      sb.append("." + getterNameForField(fieldDetails) + "()");
      builder.addStatement(sb.toString());
      return builder.build();
    }
  }

  private String getterNameForField(FieldDetails fieldDetails) {
    StringBuilder sb = new StringBuilder("get");
    sb.append(capitalize(fieldDetails.getName()));
    if (fieldDetails.isRepeated()){
      if(fieldDetails.isMapField()) {
        sb.append("Map");
      }
      else {
        sb.append("List");
      }
    }
    return sb.toString();
  }

  private static String capitalize(String name) {
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }
  
  private static String lowerCamel(String name) {
    return name.substring(0, 1).toLowerCase() + name.substring(1);
  }

  private TypeName wrapped(Class<?> javaClass) {
    //System.out.println(javaClass.toString() + " vs " + currentlyProcessing.toString());
    if (com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(javaClass)) {
      if (!currentlyProcessing.equals(javaClass) && !todo.contains(javaClass) && !units.containsKey(javaClass)) {
        this.addClass(javaClass);
      }
      return ClassName.get(packageName, classNameMap.get(javaClass));
    }
    return ClassName.get(javaClass);
  }

  public String getJavaFileForClass(Class<?> clazz) {
    return units.get(clazz).build().toString();
  }
  
  public String getWrappedClassName(Class<?> clazz) {
    if (!com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(clazz)) {
      return clazz.getCanonicalName();
    }
    
    if(!classNameMap.containsKey(clazz)) {
      
      System.out.println("new class: "+clazz.getCanonicalName());      
      List<Class<?>> collisions = classNameMap.keySet().stream().filter(x -> x.getSimpleName().equals(clazz.getSimpleName()))
          .collect(Collectors.toList());
      collisions.stream().forEach(x -> System.out.println("EXISTING: "+x.getCanonicalName()));

      if (collisions.size() > 0) {
        System.out.println("long name: "+PREFIX+classNameToCamelCase(clazz));
        classNameMap.put(clazz, PREFIX+classNameToCamelCase(clazz));
      } else {
        classNameMap.put(clazz, PREFIX+clazz.getSimpleName());
      }
      System.out.println("with name: "+classNameMap.get(clazz));
    }
    return classNameMap.get(clazz);   
  }
  
  public Set<Class<?>> getGeneratedClasses(){
    return units.keySet();
  }

}
