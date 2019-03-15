package io.github.darkantoine.protowrapper;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;

import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class ClassBuilder {
  
  private TypeSpec.Builder classBuilder;
  WrapperBuilder wrapperBuilder;
  Class<?> protoClass;
  ProtoLoader protoLoader;
  String className;
  String protoFieldName;
  
  public ClassBuilder(Class<?> protoClass, WrapperBuilder wrapperBuilder) {
    
    this.protoClass=protoClass;
    this.wrapperBuilder= wrapperBuilder;
    className = wrapperBuilder.getClassNameMap().get(protoClass);
    protoLoader = wrapperBuilder.getProtoLoaders().get(protoClass);

    classBuilder = TypeSpec.classBuilder(className)
        .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
        .superclass(wrapperBuilder.getAbstractClass()==null?Object.class:wrapperBuilder.getAbstractClass());    

    protoFieldName = StringUtils.lowerCamel(className) + "PB";
    classBuilder.addField(protoClass, protoFieldName, Modifier.PRIVATE);

    addConstructor();
    addMethods();

  }
  
  public TypeSpec build() {
    
    return classBuilder.build();
  }

  private void addConstructor() {
    MethodSpec.Builder mb;
    mb = MethodSpec.constructorBuilder().addParameter(protoClass, protoFieldName);
    mb.addModifiers(Modifier.PUBLIC);
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("this." + protoFieldName + "= " + protoFieldName);
    mb.addCode(builder.build());
    classBuilder.addMethod(mb.build());

  }
  
  private void addMethods() {
    protoLoader.getMethodsMap().values().stream().forEach(this::addMethod);
  }

  private void addMethod(FieldDetails fieldDetails) {
    MethodSpec.Builder mb = MethodSpec.methodBuilder(getterNameForField(fieldDetails))
        .returns(wrapperBuilder.computeReturnType(fieldDetails));
    mb.addModifiers(Modifier.PUBLIC);
    mb.addCode(codeBlockForField(fieldDetails));
    classBuilder.addMethod(mb.build());
  }
  
  private CodeBlock codeBlockForField(FieldDetails fieldDetails) {


    if (JavaType.MESSAGE.equals(fieldDetails.getJavaType())) {
      if (!fieldDetails.isRepeated()) {
        if (!ClassUtils.isGoogleClass(fieldDetails.getJavaClass())) {
          return codeBlockForWrappedField(fieldDetails);
        }
      } else {
        if (ClassUtils.isGoogleClass(fieldDetails.getJavaClass())
            || !com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(fieldDetails.getJavaClass())) {
          return codeBlockForSimpleField(fieldDetails);
        } else {
          if (fieldDetails.isMapField()) {

         return codeBlockForMapField(fieldDetails);
          } else {

            return codeBlockForListField(fieldDetails);
          }
        }
      }
    }
    return codeBlockForSimpleField(fieldDetails);

  }
  
  private CodeBlock codeBlockForListField(FieldDetails fieldDetails) {
    CodeBlock.Builder builder = CodeBlock.builder();
    TypeName classType = wrapped(fieldDetails.getJavaClass());
    builder.addStatement("$T<$T> list = new $T<$T>()", List.class, classType, wrapperBuilder.getListClass(), classType);
    builder.beginControlFlow("for($T item:" + protoFieldName + "." + getterNameForField(fieldDetails) + "())",
        fieldDetails.getJavaClass()).addStatement("list.add(new $T(item))", classType).endControlFlow();
    builder.addStatement("return list");
    return builder.build();
  }

  private CodeBlock codeBlockForSimpleField(FieldDetails fieldDetails) {
    StringBuilder sb = new StringBuilder();
    CodeBlock.Builder builder = CodeBlock.builder();
    sb.append("return ");
    sb.append(protoFieldName);
    sb.append("." + getterNameForField(fieldDetails) + "()");
    builder.addStatement(sb.toString());
    return builder.build();
  }

  private CodeBlock codeBlockForWrappedField(FieldDetails fieldDetails) {
    StringBuilder sb = new StringBuilder();
    sb.append("return new $T");
    sb.append("(" + protoFieldName);
    sb.append("." + getterNameForField(fieldDetails) + "())");
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement(sb.toString(), wrapped(fieldDetails.getJavaClass()));
    return builder.build();
  }

  private CodeBlock codeBlockForMapField(FieldDetails fieldDetails) {
    CodeBlock.Builder builder = CodeBlock.builder();
          TypeName classType = wrapped(fieldDetails.getJavaClass());
    TypeName keyClass = wrapped(fieldDetails.getMapKeyClass());
    builder.addStatement("$T<$T,$T> map = new $T<$T,$T>()", Map.class, keyClass, classType, wrapperBuilder.getMapClass(), keyClass,
        classType);
    builder
        .beginControlFlow(
            "for($T item: " + protoFieldName + "." + getterNameForField(fieldDetails) + "().keySet())",
            keyClass)
        .addStatement("map.put(item, new $T(" + protoFieldName + "." + getterNameForField(fieldDetails)
            + "().get(item)))", classType)
        .endControlFlow();
    builder.addStatement("return map");
    return builder.build();
  }

  private TypeName wrapped(Class<?> clazz) {
    return wrapperBuilder.getWrappedTypeName(clazz);
  }
  
  private static String getterNameForField(FieldDetails fieldDetails) {
    StringBuilder sb = new StringBuilder("get");
    sb.append(StringUtils.capitalize(fieldDetails.getName()));
    if (fieldDetails.isRepeated()) {
      if (fieldDetails.isMapField()) {
        sb.append("Map");
      } else {
        sb.append("List");
      }
    }
    return sb.toString();
  }


}
