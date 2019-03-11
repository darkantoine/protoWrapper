package io.github.darkantoine.protowrapper;

import static org.junit.Assert.*;

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
  public void test() {
    System.out.println(wb.getJavaFileForClass(Person.class));
    assert(true);
  }

}
