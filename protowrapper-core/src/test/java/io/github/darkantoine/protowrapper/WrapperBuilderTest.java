package io.github.darkantoine.protowrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    
    System.out.println(wb.getJavaFileForClass(Person.class));
    
    
    Path path = Paths.get("target/generated-test-sources/java/com/example");
    if (!Files.exists(path)) {
      Files.createDirectories(path);
    }
    Files.list(path).forEach(WrapperBuilderTest::deleteFile);
    for(Class<?> generatedClass: wb.getGeneratedClasses()) {
    System.out.println("generatedClass: "+generatedClass.getName());
     path = Paths.get("target/generated-test-sources/java/"+packageToFolderPath(wb.getPackageForClass(generatedClass)));
     if (!Files.exists(path)) {
       Files.createDirectories(path);
     }
    Files.write(Paths.get("target/generated-test-sources/java/"+packageToFolderPath(wb.getPackageForClass(generatedClass))+"/"+wb.getWrappedClassName(generatedClass)+".java"),wb.getJavaFileForClass(generatedClass).getBytes());
    }
    assert(true);
  }
  
  private static String packageToFolderPath(String packageName) {
    return packageName.replace('.', '/');
  }
  
  private static void deleteFile(Path path) {
    if(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if(Files.isRegularFile(path)) {
        try {
          Files.delete(path);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }
  


}
