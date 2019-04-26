package io.github.darkantoine.protowrapper;

import com.amadeus.pulse.rule.facade.AbstractPulseFacade;
import com.squareup.javapoet.JavaFile;

public class CustomisedWrapperBuilder extends WrapperBuilder {

  public CustomisedWrapperBuilder() {
    this(AbstractPulseFacade.class);
  }

  public CustomisedWrapperBuilder(Class<?> abstractClass, String prefix, String suffix) {
    super(abstractClass, prefix, suffix);
  }

  public CustomisedWrapperBuilder(Class<?> abstractClass) {
    super(abstractClass);
  }
  
  protected void build(Class<?> protoClass) {
    ClassBuilder classBuilder = new CustomisedClassBuilder (protoClass, this);
    wrappedJavaFilesMap.put(protoClass, JavaFile.builder(getPackageName(protoClass), classBuilder.build()));
  }

}
