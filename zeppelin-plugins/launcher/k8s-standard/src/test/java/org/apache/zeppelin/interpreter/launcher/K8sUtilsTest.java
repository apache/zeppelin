package org.apache.zeppelin.interpreter.launcher;

import static org.junit.Assert.*;

import org.junit.Test;

public class K8sUtilsTest {

  @Test
  public void testConvert() {
    assertEquals("484Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100m"));
    assertEquals("1408Mi", K8sUtils.calculateMemoryWithDefaultOverhead("1Gb"));
    assertEquals("4505Mi", K8sUtils.calculateMemoryWithDefaultOverhead("4Gb"));
    assertEquals("6758Mi", K8sUtils.calculateMemoryWithDefaultOverhead("6Gb"));
    assertEquals("9011Mi", K8sUtils.calculateMemoryWithDefaultOverhead("8Gb"));
    // some extrem values
    assertEquals("112640Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100Gb"));
    assertEquals("115343360Mi", K8sUtils.calculateMemoryWithDefaultOverhead("100Tb"));
  }

  @Test(expected = NumberFormatException.class)
  public void testExceptionMaxLong() {
    K8sUtils.calculateMemoryWithDefaultOverhead("10000000Tb");
  }

  @Test(expected = NumberFormatException.class)
  public void testExceptionNoValidNumber() {
    K8sUtils.calculateMemoryWithDefaultOverhead("NoValidNumber10000000Tb");
  }
}
