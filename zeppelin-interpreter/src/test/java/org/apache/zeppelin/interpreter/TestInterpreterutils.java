package org.apache.zeppelin.interpreter;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class TestInterpreterutils {
  
  @Test
  public void  testSubstitution() {
    Properties properties = new Properties ();
    properties.put("substitute", "${name,default_value}");
    properties.put("regular", "value");
    Map<String, Object> map = new HashMap<>();
    map.put("name", "userValue");
    
    Properties props = InterpreterUtils.substitute(properties, map);
    
    assertEquals (2, props.size());
    assertEquals("userValue", props.get("substitute"));
    assertEquals("value", props.get("regular"));
  }
  
  @Test
  public void  testDefaultSubstitution() {
    Properties properties = new Properties ();
    properties.put("substitute", "${name,default_value}");
    properties.put("regular", "value");
    
    Properties props = InterpreterUtils.substitute(properties, null);
    
    assertEquals (2, props.size());
    assertEquals("default_value", props.get("substitute"));
    assertEquals("value", props.get("regular"));
  }
  
  @Test
  public void  testDefaultSubstitutionWithEmptyConfig() {
    Properties properties = new Properties ();
    properties.put("substitute", "${name,default_value}");
    properties.put("regular", "value");
    
    Map<String, Object> map = new HashMap<>();
    Properties props = InterpreterUtils.substitute(properties, map);
    
    assertEquals (2, props.size());
    assertEquals("default_value", props.get("substitute"));
    assertEquals("value", props.get("regular"));
  }
  
  @Test
  public void  testSubstitutionWithStringWithCommas() {
    Properties properties = new Properties ();
    properties.put("substitute1", "${name1,default_value1,default_value2}");
    properties.put("substitute2", "${name,default_value1,default_value2}");
    properties.put("regular", "value");
    
    Map<String, Object> map = new HashMap<>();
    map.put("name1", "userValue1,userValue2");
    Properties props = InterpreterUtils.substitute(properties, map);
    
    
    assertEquals (3, props.size());
    assertEquals("userValue1,userValue2", props.get("substitute1"));
    assertEquals("default_value1,default_value2", props.get("substitute2"));
    assertEquals("value", props.get("regular"));
  }

}
