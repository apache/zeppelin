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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonatype.aether.RepositoryException;

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

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Test
  public void testAddRepo() {
    int reposCnt = resolver.getRepos().size();
    resolver.addRepo("securecentral", "https://repo1.maven.org/maven2", false);
    assertEquals(reposCnt + 1, resolver.getRepos().size());
  }

  @Test
  public void testDelRepo() {
    int reposCnt = resolver.getRepos().size();
    resolver.delRepo("securecentral");
    resolver.delRepo("badId");
    assertEquals(reposCnt - 1, resolver.getRepos().size());
  }

  @Test
  public void testLoad() throws Exception {
    // basic load
    resolver.load("com.databricks:spark-csv_2.10:1.3.0", testCopyPath);
    assertEquals(new File(home + "/" + testCopyPath).list().length, 4);
    FileUtils.cleanDirectory(new File(home + "/" + testCopyPath));

    // load with exclusions parameter
    resolver.load("com.databricks:spark-csv_2.10:1.3.0",
        Collections.singletonList("org.scala-lang:scala-library"), testCopyPath);
    assertEquals(new File(home + "/" + testCopyPath).list().length, 3);
    FileUtils.cleanDirectory(new File(home + "/" + testCopyPath));

    // load from added repository
    resolver.addRepo("sonatype", "https://oss.sonatype.org/content/repositories/agimatec-releases/", false);
    resolver.load("com.agimatec:agimatec-validation:0.9.3", testCopyPath);
    assertEquals(new File(home + "/" + testCopyPath).list().length, 8);

    // load invalid artifact
    resolver.delRepo("sonatype");
    exception.expect(RepositoryException.class);
    resolver.load("com.agimatec:agimatec-validation:0.9.3", testCopyPath);
  }
}