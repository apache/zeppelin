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

package org.apache.zeppelin.dep;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DependencyResolverTest {
  private static DependencyResolver resolver;
  private static String testPath;
  private static String testCopyPath;
  private static String home;
  
  @BeforeClass
  public static void setUp() throws Exception {
    testPath = "test-repo";
    testCopyPath = "test-copy-repo";
    resolver = new DependencyResolver(testPath);
    home = System.getenv("ZEPPELIN_HOME");
    if (home == null) {
      home = System.getProperty("zeppelin.home");
    }
    if (home == null) {
      home = "..";
    }
  }
  
  @AfterClass
  public static void tearDown() throws Exception {
    FileUtils.deleteDirectory(new File(home + "/" + testPath));
    FileUtils.deleteDirectory(new File(home + "/" + testCopyPath)); 
  }    
  
  @Test
  public void testLoad() throws Exception {
    resolver.load("org.apache.commons:commons-lang3:3.4", testCopyPath);

    assertTrue(new File(home + "/" + testPath + "/org/apache/commons/commons-lang3/3.4/").exists());
    assertTrue(new File(home + "/" + testCopyPath + "/commons-lang3-3.4.jar").exists());
  }
}