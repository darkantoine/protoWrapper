package io.github.darkantoine.protowrapper;

import static io.github.darkantoine.protowrapper.StringUtils.capitalize;

import java.util.List;

import javax.lang.model.element.Modifier;

import com.amadeus.pulse.message.GenericEventMessage.EventContainer;
import com.amadeus.pulse.message.GenericEventMessage.GenericEvent;
import com.amadeus.pulse.rule.facade.AbstractPulseFacade;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import io.github.darkantoine.protowrapper.ProtoLoader.FieldDetails;

public class CustomisedClassBuilder extends ClassBuilder {

  public CustomisedClassBuilder(Class<?> protoClass, WrapperBuilder wrapperBuilder) {
    super(protoClass, wrapperBuilder);
  }
  
  @Override
  protected void addConstructors() {
    if(wrapperBuilder.getClassesParameters().containsKey(protoClass) && wrapperBuilder.getClassesParameters().get(protoClass).isRootClass) {
      addRootConstructor();
    }
    else {
      addNestedFacadeConstructor();
    }
  }
  
  private void addRootConstructor() {
    MethodSpec.Builder mb;
    mb = MethodSpec.constructorBuilder().addParameter(protoClass, protoFieldName)
        .addParameter(GenericEvent.class, "event");
    mb.addModifiers(Modifier.PUBLIC);
    CodeBlock.Builder builder = CodeBlock.builder()
        .addStatement("super($N)", "event")
        .addStatement("this.$N = $N",protoFieldName,protoFieldName);
    mb.addCode(builder.build());
    classBuilder.addMethod(mb.build());    
  }

  private void addNestedFacadeConstructor() {
    MethodSpec.Builder mb;
    mb = MethodSpec.constructorBuilder().addParameter(protoClass, protoFieldName)
        .addParameter(AbstractPulseFacade.class, "parentFacade")
        .addParameter(ParameterizedTypeName.get(ClassName.get(List.class),TypeName.get(EventContainer.class)), "events");
    mb.addModifiers(Modifier.PUBLIC);
    CodeBlock.Builder builder = CodeBlock.builder()
        .addStatement("super($N,$N)", "parentFacade","events")
        .addStatement("this.$N = $N",protoFieldName,protoFieldName);
    mb.addCode(builder.build());
    classBuilder.addMethod(mb.build());        
  }
  
  protected CodeBlock codeBlockForListField(FieldDetails fieldDetails) {

    TypeName returnType = wrapperBuilder.computeReturnType(fieldDetails);
    String cachedFieldName = CACHED_PREFIX + capitalize(fieldDetails.getName());
    classBuilder.addField(returnType, cachedFieldName, Modifier.PRIVATE);

    TypeName classType = wrapped(fieldDetails.getJavaClass());
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.beginControlFlow("if ($N==null)", cachedFieldName)
        .addStatement("$N = new $T<$T>()", cachedFieldName, wrapperBuilder.getListClass(), classType)
        .addStatement("int i=0")
        .beginControlFlow("for($T item:" + protoFieldName + "." + getterNameForField(fieldDetails) + "())",
            fieldDetails.getJavaClass())
        .addStatement("$N.add(new $T("+constructorParametersForListField(fieldDetails)+"))", cachedFieldName, classType)
        .addStatement("i++")
        .endControlFlow().endControlFlow()
        .addStatement("return $N", cachedFieldName);
    return builder.build();
  }
  
  protected String constructorParametersForListField(FieldDetails fieldDetails) {   
      return "item, this, this.getEventsForSubElement("+fieldDetails.getFieldDescriptor().getNumber()+", String.valueOf(i))";
  }
  protected String constructorParametersForWrappedField(FieldDetails fieldDetails) {
    return protoFieldName + '.' + getterNameForField(fieldDetails) + "(),this, this.getEventsForSubElement("+fieldDetails.getFieldDescriptor().getNumber()+", NON_REPEATED_FIELD_ID)";
  }
  protected String constructorParametersForMapField(FieldDetails fieldDetails) {
    return protoFieldName + "." + getterNameForField(fieldDetails) + "().get(item),this, this.getEventsForSubElement("+fieldDetails.getFieldDescriptor().getNumber()+", item)";
  }
  
  protected void addMethods() {
    protoLoader.getMethodsMap().values().stream().filter(field -> !field.getName().equals("id")).forEach(super::addMethod);
    
    if(protoLoader.getClassDescriptor().findFieldByName("id") == null) {
      addFakeGetItMethod();
    }
    else {
      FieldDetails fieldDetails = protoLoader.getMethodsMap().get(protoLoader.getClassDescriptor().findFieldByName("id").getNumber());
      
      if(wrapperBuilder.getWrappedTypeName(fieldDetails.getJavaClass()).equals(TypeName.get(String.class))){
        super.addMethod(fieldDetails);
      }
      else {
        addConvertedToStringGetItMethod(fieldDetails);
      }
    }
  }
  
  private void addConvertedToStringGetItMethod(FieldDetails fieldDetails) {
    MethodSpec.Builder mb = MethodSpec.methodBuilder("getId")
        .returns(String.class);
    mb.addModifiers(Modifier.PUBLIC);
    
    StringBuilder sb = new StringBuilder();
    CodeBlock.Builder builder = CodeBlock.builder();
    if(fieldDetails.getJavaClass().isPrimitive()) {
    sb.append("return valueOf( ");
    sb.append(protoFieldName);
    sb.append(".getId())");    
    }
    else {
      sb.append("return ");
      sb.append(protoFieldName);
      sb.append(".getId().toString()");    
    }    
    builder.addStatement(sb.toString());   
    
    mb.addCode(builder.build());
    classBuilder.addMethod(mb.build());
    
  }

  private void addFakeGetItMethod() {
    MethodSpec.Builder mb = MethodSpec.methodBuilder("getId")
        .returns(String.class);
    mb.addModifiers(Modifier.PUBLIC);
    mb.addCode(CodeBlock.builder().addStatement("return this.toString()").build()
        );
    classBuilder.addMethod(mb.build());
    
  }

 
}
