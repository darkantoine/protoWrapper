package io.github.darkantoine.protowrapper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

import com.example.test.PersonMessage.Person;

public class WrapperBuilderTest {

  private WrapperBuilder wb;
  @Before
  public void setUp() throws Exception {
    wb = new WrapperBuilder();
    wb.addClass(Person.class);
    wb.build();    
  }

  @Test
  public void test() throws IOException {
    Path path = Paths.get("target/generated-test-sources/java/com/example");
    if (!Files.exists(path)) {
      Files.createDirectories(path);
    }
    for(Class<?> generatedClass: wb.getGeneratedClasses()) {
    System.out.println("generatedClass: "+generatedClass.getName());
      System.out.println(wb.getJavaFileForClass(generatedClass)); 
    bufferedWritter("target/generated-test-sources/java/com/example/"+wb.getWrappedClassName(generatedClass)+".java",wb.getJavaFileForClass(generatedClass));
    }
    assert(true);
  }
  
  private void bufferedWritter(String fileName, String str) 
      throws IOException {    
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(str);         
        writer.close();
    }


}
