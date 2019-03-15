package io.github.darkantoine.protowrapper;

import java.lang.reflect.Modifier;

public class ClassUtils {
  
  private ClassUtils() {
    
  }
    
  public static boolean isGoogleClass(Class<?> javaClass) {
   return javaClass.getCanonicalName().startsWith("com.google.protobuf");
  }

  public static boolean isProtocolBufferMessage(Class<?> clazz) {
   return com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(clazz) && !ClassUtils.isGoogleClass(clazz);
  }

  public static boolean isAbstract(Class<?> abstractClass) {
   return Modifier.isAbstract(abstractClass.getModifiers());
  }


}
