/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
