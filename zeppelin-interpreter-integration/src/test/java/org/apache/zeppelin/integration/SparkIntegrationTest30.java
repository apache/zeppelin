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

package org.apache.zeppelin.integration;

import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(value = Parameterized.class)
public class SparkIntegrationTest30 extends SparkIntegrationTest {

  public SparkIntegrationTest30(String sparkVersion, String hadoopVersion) {
    super(sparkVersion, hadoopVersion);
  }

  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {"3.0.0", "2.7"},
            {"3.0.0", "3.2"}
    });
  }

  @Override
  protected void setUpSparkInterpreterSetting(InterpreterSetting interpreterSetting) {
    // spark3 doesn't support yarn-client and yarn-cluster any more, use
    // spark.master and spark.submit.deployMode instead
    String sparkMaster = interpreterSetting.getJavaProperties().getProperty("spark.master");
    if (sparkMaster.equals("yarn-client")) {
      interpreterSetting.setProperty("spark.master", "yarn");
      interpreterSetting.setProperty("spark.submit.deployMode", "client");
    } else if (sparkMaster.equals("yarn-cluster")){
      interpreterSetting.setProperty("spark.master", "yarn");
      interpreterSetting.setProperty("spark.submit.deployMode", "cluster");
    } else if (sparkMaster.startsWith("local")) {
      interpreterSetting.setProperty("spark.submit.deployMode", "client");
    }
  }
}
