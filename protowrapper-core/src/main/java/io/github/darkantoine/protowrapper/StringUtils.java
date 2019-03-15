package io.github.darkantoine.protowrapper;

public class StringUtils {
  
  private StringUtils() {
    
  }
  
  public static String capitalize(String name) {
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  public static String lowerCamel(String name) {
    return name.substring(0, 1).toLowerCase() + name.substring(1);
  }

}
