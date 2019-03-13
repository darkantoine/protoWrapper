package io.github.darkantoine.protowrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.ByteString;
import com.google.protobuf.Type;

public class ProtoLoader {

  private final Class<? extends Type> protoClass;
  private final Descriptor classDescriptor;
  private Map<Integer, FieldDetails> methodsMap = new HashMap<Integer, FieldDetails>();

  @SuppressWarnings("unchecked")
  public ProtoLoader(Class<?> inputClass) {
    if (Type.class.isAssignableFrom(inputClass)) {
      throw new IllegalArgumentException();
    }
    protoClass = (Class<? extends Type>)inputClass;
    Method method = null;
    try {
      method = protoClass.getMethod("getDescriptor", new Class<?>[] {});
    } catch (NoSuchMethodException | SecurityException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new IllegalArgumentException();
    }
    try {
      classDescriptor = (Descriptor)method.invoke(null, new Object[] {});
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw new IllegalArgumentException();
    }
    initMethodsMaps(protoClass);
  }

  private void initMethodsMaps(Class<? extends Type> protoClass) {
    for (FieldDescriptor fieldDescriptor : classDescriptor.getFields()) {
      methodsMap.put(fieldDescriptor.getIndex(), new FieldDetails(fieldDescriptor, protoClass));
    }
  }

  public String getClassFullName() {
    return classDescriptor.getFullName();
  }

  public Map<Integer, FieldDetails> getMethodsMap() {
    return methodsMap;
  }

  public static class FieldDetails {
    private FieldDescriptor fieldDescriptor;
    private String name;
    private boolean isRepeated;
    private boolean isMapField;
    private JavaType javaType;
    private String messageName;
    private Class<?> javaClass;
    private Class<?> mapKeyClass;

    public FieldDetails(FieldDescriptor fD, Class<?> parentClass) {
      fieldDescriptor = fD;
      name = fD.getJsonName();
      isRepeated = fD.isRepeated();
      isMapField = fD.isMapField();
      javaType = fD.getJavaType();
      //System.out.println("processing field: "+fD.getJsonName());
      computeJavaClass(parentClass);
    }

    private void computeJavaClass(Class<?> parentClass) {
      
      if(JavaType.BOOLEAN.equals(javaType)) {
        javaClass = Boolean.class;
        return;
      }
      
      if(JavaType.DOUBLE.equals(javaType)) {
        javaClass = Double.class;
        return;
      }    
      
      if(JavaType.FLOAT.equals(javaType)) {
        javaClass = Float.class;
        return;
      }    
          
      if(JavaType.INT.equals(javaType)) {
        javaClass = Integer.class;
        return;
      }
      
      if(JavaType.LONG.equals(javaType)) {
        javaClass = Long.class;
        return;
      }    

      if(JavaType.STRING.equals(javaType)) {
        javaClass = String.class;
        return;
      }

      if (JavaType.BYTE_STRING.equals(javaType)) {
        javaClass = ByteString.class;
        return;
      }
      
      if (JavaType.ENUM.equals(javaType)) {
        try {
          Method method = parentClass.getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
          javaClass = method.getReturnType();
        } catch (NoSuchMethodException | SecurityException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        
        return;
      }
      

      if (javaType == JavaType.MESSAGE) {
        try {
          messageName = fieldDescriptor.getMessageType().getFullName();
          if (!isMapField) {

            if (!isRepeated) {
              javaClass = parentClass.getMethod(getMethodName(name,isMapField,isRepeated))
                  .getReturnType();
            } else {

              Method method = parentClass
                  .getMethod(getMethodName(name,isMapField,isRepeated));
              java.lang.reflect.Type type = method.getGenericReturnType();
              if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType)type;
                java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
                javaClass = (Class<?>)arg;
              }

            }
          } else {

            Method method = parentClass.getMethod(getMethodName(name,isMapField,isRepeated));
            java.lang.reflect.Type type = method.getGenericReturnType();
            if (type instanceof ParameterizedType) {
              ParameterizedType pt = (ParameterizedType)type;
              javaClass = (Class<?>)pt.getActualTypeArguments()[1];
              mapKeyClass = (Class<?>)pt.getActualTypeArguments()[0];
            }
          }
        } catch (NoSuchMethodException | SecurityException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    private static String getMethodName(String name, boolean isMapField, boolean isRepeated) {
      if(!isRepeated) {
         return "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
      }
      else {
        if(isMapField) {
          return "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        } else {
          return "get" + name.substring(0, 1).toUpperCase() + name.substring(1) + "List";
        }
      }
    }

    public String getName() {
      return name;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("{ name=");
      sb.append(name);
      sb.append(", isRepeated=");
      sb.append(isRepeated);
      sb.append(", isMapField=");
      sb.append(isMapField);
      sb.append(", javaType=");
      sb.append(javaType.name());
      if (messageName != null) {
        sb.append(", messageName=");
        sb.append(messageName);
      }
      if (javaClass != null) {
        sb.append(", javaClass=");
        sb.append(javaClass.getName());
      }
      if (mapKeyClass != null) {
        sb.append(", mapKeyClass=");
        sb.append(mapKeyClass.getName());
      }
      sb.append(" }");
      return sb.toString();
    }

    public FieldDescriptor getFieldDescriptor() {
      return fieldDescriptor;
    }
    
    public boolean isRepeated() {
      return isRepeated;
    }
    
    public boolean isMapField() {
      return isMapField;
    }
    
    public JavaType getJavaType() {
      return javaType;
    }
    
    public Class<?> getJavaClass(){
      return javaClass;
    }
    
    public Class<?> getMapKeyClass(){
      return mapKeyClass;
    }

  }
}
