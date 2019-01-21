package org.apache.zeppelin.interpreter;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(value = Parameterized.class)
public class SparkIntegrationTestPt1 extends SparkIntegrationTest{

  public SparkIntegrationTestPt1(String sparkVersion) {
    super(sparkVersion);
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {"2.4.0"},
            {"2.3.2"},
            {"2.2.1"},
    });
  }

}
