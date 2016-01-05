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

package org.apache.zeppelin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UtilsForTests {

  static Logger LOGGER = LoggerFactory.getLogger(UtilsForTests.class);

  public static File createTmpDir() throws Exception {
    File tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    tmpDir.mkdir();
    return tmpDir;

  }
  /*
	private static final String HADOOP_DIST="http://apache.mirror.cdnetworks.com/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz";
	//private static final String HADOOP_DIST="http://www.us.apache.org/dist/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz";

	public static void getHadoop() throws MalformedURLException, IOException{
		setEnv("HADOOP_HOME", new File("./target/hadoop-1.2.1").getAbsolutePath());
		if(new File("./target/hadoop-1.2.1").isDirectory()) return;
		//System.out.println("Downloading a hadoop distribution ... it will take a while");
		//FileUtils.copyURLToFile(new URL(HADOOP_DIST), new File("/tmp/zp_test_hadoop-bin.tar.gz"));
		System.out.println("Unarchive hadoop distribution ... ");
		new File("./target").mkdir();
		Runtime.getRuntime().exec("tar -xzf /tmp/zp_test_hadoop-bin.tar.gz -C ./target");
	}
	*/

  public static void delete(File file) {
    if (file.isFile()) file.delete();
    else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
  }

  /**
   * Utility method to create a file (if does not exist) and populate it the the given content
   *
   * @param path    to file
   * @param content of the file
   * @throws IOException
   */
  public static void createFileWithContent(String path, String content) throws IOException {
    File f = new File(path);
    if (!f.exists()) {
      stringToFile(content, f);
    }
  }

  public static void stringToFile(String string, File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    out.write(string.getBytes());
    out.close();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void setEnv(String k, String v) {
    Map<String, String> newenv = new HashMap<String, String>();
    newenv.put(k, v);
    try {
      Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
      Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
      theEnvironmentField.setAccessible(true);
      Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
      env.putAll(newenv);
      Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
      theCaseInsensitiveEnvironmentField.setAccessible(true);
      Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
      cienv.putAll(newenv);
    } catch (NoSuchFieldException e) {
      try {
        Class[] classes = Collections.class.getDeclaredClasses();
        Map<String, String> env = System.getenv();
        for (Class cl : classes) {
          if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Object obj = field.get(env);
            Map<String, String> map = (Map<String, String>) obj;
            map.clear();
            map.putAll(newenv);
          }
        }
      } catch (Exception e2) {
        LOGGER.error(e2.toString(), e2);
      }
    } catch (Exception e1) {
      LOGGER.error(e1.toString(), e1);
    }
  }
}
