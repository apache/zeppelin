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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.ExecutionContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class K8sMinikubeTestPySpark {
    private static MiniZeppelin zeppelin;
    private static InterpreterFactory interpreterFactory;
    private static InterpreterSettingManager interpreterSettingManager;

    @BeforeClass
    public static void setUp() throws IOException {
        ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
        zconf.setRunMode(ZeppelinConfiguration.RUN_MODE.K8S);
        zconf.setProperty("zeppelin.k8s.portforward", "true");
        zconf.setProperty("zeppelin.interpreter.connect.timeout", "600000");

        zeppelin = new MiniZeppelin();
        zeppelin.start(K8sMinikubeTestPySpark.class, zconf);
        interpreterFactory = zeppelin.getInterpreterFactory();
        interpreterSettingManager = zeppelin.getInterpreterSettingManager();
    }


    @AfterClass
    public static void tearDown() throws IOException {
        if (zeppelin != null) {
            zeppelin.stop();
        }
    }

    @Test
    public void testK8sStartPySparkSuccessful() throws InterpreterException {
        // given
        InterpreterSetting interpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");

        // zeppelin settings
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.image", "local/zeppelin");
        interpreterSetting.setProperty("zeppelin.k8s.interpreter.container.imagePullPolicy", "Never");
        interpreterSetting.setProperty("ZEPPELIN_CONF_DIR", "/opt/zeppelin/conf");
        interpreterSetting.setProperty("ZEPPELIN_HOME", "/opt/zeppelin");
        interpreterSetting.setProperty("zeppelin.k8s.spark.container.image", "local/spark-py:latest");
        interpreterSetting.setProperty("zeppelin.k8s.spark.container.imagePullPolicy", "Never");

        interpreterSetting.setProperty("SPARK_PRINT_LAUNCH_COMMAND", "true");
        interpreterSetting.setProperty("zeppelin.spark.scala.color", "false");
        interpreterSetting.setProperty("zeppelin.spark.deprecatedMsg.show", "false");
        interpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");
        interpreterSetting.setProperty("zeppelin.pyspark.useIPython", "false");

        // spark settings
        interpreterSetting.setProperty("SPARK_HOME", "/spark");
        interpreterSetting.setProperty("spark.master", "k8s://https://kubernetes.default.svc");
        interpreterSetting.setProperty("spark.kubernetes.container.image", "local/spark-py:latest");
        interpreterSetting.setProperty("spark.kubernetes.container.image.pullPolicy", "Never");
        interpreterSetting.setProperty("zeppelin.spark.enableSupportedVersionCheck", "false");
        interpreterSetting.setProperty("PYSPARK_PYTHON", "python3");

        //Note that the GitHub-hosted runners (ubuntu-20.04) has only 2-core CPU and 7 GB of RAM memory. (https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners)
        // spark driver
        interpreterSetting.setProperty("spark.driver.memory", "1g");
        interpreterSetting.setProperty("spark.driver.cores", "500m");
        interpreterSetting.setProperty("spark.kubernetes.driver.request.cores", "500m");

        // spark executor
        interpreterSetting.setProperty("spark.executor.memory", "1g");
        interpreterSetting.setProperty("spark.executor.instances", "1");
        interpreterSetting.setProperty("spark.kubernetes.executor.request.cores","500m");

        // test spark interpreter
        Interpreter interpreter = interpreterFactory.getInterpreter("spark.spark", new ExecutionContext("user1", "note1", "test"));
        InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").build();

        InterpreterResult interpreterResult = interpreter.interpret("sc.range(1,10).sum()", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
        assertTrue(interpreterResult.toString(), interpreterResult.message().get(0).getData().contains("45"));

        // test PySparkInterpreter
        Interpreter pySparkInterpreter = interpreterFactory.getInterpreter("spark.pyspark", new ExecutionContext("user1", "note1", "test"));
        interpreterResult = pySparkInterpreter.interpret("sqlContext.createDataFrame([(1,'a'),(2,'b')], ['id','name']).registerTempTable('test')", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());


        // test IPySparkInterpreter
        Interpreter ipySparkInterpreter = interpreterFactory.getInterpreter("spark.ipyspark", new ExecutionContext("user1", "note1", "test"));
        interpreterResult = ipySparkInterpreter.interpret("sqlContext.table('test').show()", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());

        // test SparkSQLInterpreter
        Interpreter sqlInterpreter = interpreterFactory.getInterpreter("spark.sql", new ExecutionContext("user1", "note1", "test"));
        interpreterResult = sqlInterpreter.interpret("select count(1) as c from test", context);
        assertEquals(interpreterResult.toString(), InterpreterResult.Code.SUCCESS, interpreterResult.code());
        assertEquals(interpreterResult.toString(), InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
        assertEquals(interpreterResult.toString(), "c\n2\n", interpreterResult.message().get(0).getData());
    }
}
