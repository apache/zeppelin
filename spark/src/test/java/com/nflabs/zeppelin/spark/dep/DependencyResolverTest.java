package com.nflabs.zeppelin.spark.dep;

import static org.junit.Assert.assertEquals;

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
