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

package org.apache.zeppelin.spark.dep;

import static org.junit.Assert.assertEquals;

import org.apache.zeppelin.spark.dep.DependencyResolver;
import org.junit.Test;

public class DependencyResolverTest {

  @Test
  public void testInferScalaVersion() {
    String [] version = scala.util.Properties.versionNumberString().split("[.]");
    String scalaVersion = version[0] + "." + version[1];

    assertEquals("groupId:artifactId:version",
        DependencyResolver.inferScalaVersion("groupId:artifactId:version"));
    assertEquals("groupId:artifactId_" + scalaVersion + ":version",
        DependencyResolver.inferScalaVersion("groupId::artifactId:version"));
    assertEquals("groupId:artifactId:version::test",
        DependencyResolver.inferScalaVersion("groupId:artifactId:version::test"));
    assertEquals("*",
        DependencyResolver.inferScalaVersion("*"));
    assertEquals("groupId:*",
        DependencyResolver.inferScalaVersion("groupId:*"));
    assertEquals("groupId:artifactId*",
        DependencyResolver.inferScalaVersion("groupId:artifactId*"));
    assertEquals("groupId:artifactId_" + scalaVersion,
        DependencyResolver.inferScalaVersion("groupId::artifactId"));
    assertEquals("groupId:artifactId_" + scalaVersion + "*",
        DependencyResolver.inferScalaVersion("groupId::artifactId*"));
    assertEquals("groupId:artifactId_" + scalaVersion + ":*",
        DependencyResolver.inferScalaVersion("groupId::artifactId:*"));
  }

}
