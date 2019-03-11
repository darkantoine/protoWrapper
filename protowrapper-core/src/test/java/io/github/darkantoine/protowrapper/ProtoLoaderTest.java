package io.github.darkantoine.protowrapper;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.example.test.PersonMessage.Person;

public class ProtoLoaderTest {

  private Class<?> protoClass;
  private ProtoLoader pL;
  
  @Before
  public void setup() {
    protoClass = Person.class;
    pL = new ProtoLoader(protoClass);
  }
  
  @Test
  public void testFullName() {
     assertEquals("tutorial.Person",pL.getClassFullName());
     System.out.println(com.google.protobuf.GeneratedMessageV3.class.isAssignableFrom(protoClass));
     
  }
  
  @Test
  public void testMethodsMap() {
     System.out.println(pL.getMethodsMap().toString());
  }
  
  

}
