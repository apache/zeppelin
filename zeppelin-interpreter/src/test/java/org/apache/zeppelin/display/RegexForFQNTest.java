package org.apache.zeppelin.display;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

import org.junit.Test;

public class RegexForFQNTest {

  @Test
  public void test() {
    String function = "org.keedio.sample.Utility.suma(1,2)";
    String clazz = "org.keedio.sample.Utility";
    String method = "suma(1,2)";
    
    String pattern = "(?<clazz>.+\\..+)\\.(?<method>.+)";
    
    Pattern regex = Pattern.compile(pattern);
    Matcher matcher = regex.matcher(function);
    
    matcher.find();
    
    String matcherClazz = matcher.group("clazz");
    String matcherMethod = matcher.group("method");
    
    Assert.assertTrue(clazz.equals(matcherClazz));
    Assert.assertTrue(method.equals(matcherMethod));
  }
  
}
