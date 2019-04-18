package io.github.darkantoine.protowrapper;

import static io.github.darkantoine.protowrapper.StringUtils.capitalize;
import static io.github.darkantoine.protowrapper.StringUtils.lowerCamel;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.lang.model.element.Modifier;

import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class ClassBuilder {

  private static String CACHED_PREFIX = "cached";

  private TypeSpec.Builder classBuilder;
  WrapperBuilder wrapperBuilder;
  Class<?> protoClass;
  ProtoLoader protoLoader;
  String className;
  String protoFieldName;

  public ClassBuilder(Class<?> protoClass, WrapperBuilder wrapperBuilder) {

    this.protoClass = protoClass;
    this.wrapperBuilder = wrapperBuilder;
    className = wrapperBuilder.getClassNameMap().get(protoClass);
    protoLoader = wrapperBuilder.getProtoLoaders().get(protoClass);

    classBuilder = TypeSpec.classBuilder(className).addModifiers(javax.lang.model.element.Modifier.PUBLIC)
        .superclass(wrapperBuilder.getAbstractClass() == null ? Object.class : wrapperBuilder.getAbstractClass());

    protoFieldName = StringUtils.lowerCamel(className) + "PB";
    classBuilder.addField(protoClass, protoFieldName, Modifier.PRIVATE);
    
    if (wrapperBuilder.getAbstractClass() != null) {      
      for (Constructor<?> constructor : wrapperBuilder.getAbstractClass().getConstructors()) {
        addConstructor(constructor);
      }
    }
    else {
      addConstructor();
    }
    addMethods();

  }

  private void addConstructor(Constructor<?> constructor) {
    MethodSpec.Builder mb;
    Map<String, AtomicInteger> parameterClassCount = new HashMap<String, AtomicInteger>();
    mb = MethodSpec.constructorBuilder().addParameter(protoClass, protoFieldName);
    StringBuilder sb = new StringBuilder("super(");
    Iterator<Class<?>> it = Arrays.stream(constructor.getParameterTypes()).iterator();
    while (it.hasNext()) {
      Class<?> parameterClass = it.next();
      parameterClassCount.putIfAbsent(parameterClass.getSimpleName(), new AtomicInteger(0));
      int index = parameterClassCount.get(parameterClass.getSimpleName()).incrementAndGet();
      String parameterName = lowerCamel(parameterClass.getSimpleName()) + index;
      mb.addParameter(parameterClass, parameterName);
      sb.append(parameterName);
      if (it.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append(')');
    mb.addModifiers(Modifier.PUBLIC);
    CodeBlock.Builder builder = CodeBlock.builder();
    builder
    .addStatement(sb.toString())
    .addStatement("this." + protoFieldName + "= " + protoFieldName);
    mb.addCode(builder.build());
    classBuilder.addMethod(mb.build());
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

    TypeName returnType = wrapperBuilder.computeReturnType(fieldDetails);
    String cachedFieldName = CACHED_PREFIX + capitalize(fieldDetails.getName());
    classBuilder.addField(returnType, cachedFieldName, Modifier.PRIVATE);

    TypeName classType = wrapped(fieldDetails.getJavaClass());
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.beginControlFlow("if ($N==null)", cachedFieldName)
        .addStatement("$N = new $T<$T>()", cachedFieldName, wrapperBuilder.getListClass(), classType)
        .beginControlFlow("for($T item:" + protoFieldName + "." + getterNameForField(fieldDetails) + "())",
            fieldDetails.getJavaClass())
        .addStatement("$N.add(new $T(item))", cachedFieldName, classType).endControlFlow().endControlFlow()
        .addStatement("return $N", cachedFieldName);
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

    String cachedFieldName = CACHED_PREFIX + capitalize(fieldDetails.getName());
    TypeName fieldType = wrapped(fieldDetails.getJavaClass());
    classBuilder.addField(fieldType, cachedFieldName, Modifier.PRIVATE);

    CodeBlock.Builder builder = CodeBlock.builder();

    builder.beginControlFlow("if ($N==null)", cachedFieldName)
        .addStatement("$N= new $T(" + protoFieldName + '.' + getterNameForField(fieldDetails) + "())", cachedFieldName,
            fieldType)
        .endControlFlow().addStatement("return $N", cachedFieldName);
    return builder.build();
  }

  private CodeBlock codeBlockForMapField(FieldDetails fieldDetails) {
    TypeName returnType = wrapperBuilder.computeReturnType(fieldDetails);
    String cachedFieldName = CACHED_PREFIX + capitalize(fieldDetails.getName());
    classBuilder.addField(returnType, cachedFieldName, Modifier.PRIVATE);

    CodeBlock.Builder builder = CodeBlock.builder();
    TypeName classType = wrapped(fieldDetails.getJavaClass());
    TypeName keyClass = wrapped(fieldDetails.getMapKeyClass());
    builder.beginControlFlow("if ($N==null)", cachedFieldName)
        .addStatement("$N = new $T<$T,$T>()", cachedFieldName, wrapperBuilder.getMapClass(), keyClass, classType)
        .beginControlFlow("for($T item: " + protoFieldName + "." + getterNameForField(fieldDetails) + "().keySet())",
            keyClass)
        .addStatement(
            "$N.put(item, new $T(" + protoFieldName + "." + getterNameForField(fieldDetails) + "().get(item)))",
            cachedFieldName, classType)
        .endControlFlow().endControlFlow();
    builder.addStatement("return $N", cachedFieldName);
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
