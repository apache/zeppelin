/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import com.google.gson.Gson;
import junit.framework.TestCase;
import static org.junit.Assert.*;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.Test;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.Properties;
import java.lang.Override;
import java.lang.String;


/**
 * Tests Interpreter by running pre-determined commands against mock file system
 *
 */
public class HDFSFileInterpreterTest extends TestCase {

    @Test
    public void test() {
      HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
      t.open();

      // We have info for /, /user, /tmp, /mr-history/done

      // Ensure
      // 1. ls -l works
      // 2. paths (. and ..) are correctly handled
      // 3. flags and arguments to commands are correctly handled

      InterpreterResult result1 = t.interpret("ls -l /", null);
      assertEquals(result1.type(), InterpreterResult.Type.TEXT);

      InterpreterResult result2 = t.interpret("ls -l /./user/..", null);
      assertEquals(result2.type(), InterpreterResult.Type.TEXT);

      assertEquals(result1.message(), result2.message());

      // Ensure you can do cd and after that the ls uses current directory correctly

      InterpreterResult result3 = t.interpret("cd user", null);
      assertEquals(result3.type(), InterpreterResult.Type.TEXT);
      assertEquals(result3.message(), "OK");

      InterpreterResult result4 = t.interpret("ls", null);
      assertEquals(result4.type(), InterpreterResult.Type.TEXT);

      InterpreterResult result5 = t.interpret("ls /user", null);
      assertEquals(result5.type(), InterpreterResult.Type.TEXT);

      assertEquals(result4.message(), result5.message());

      // Ensure pwd works correctly

      InterpreterResult result6 = t.interpret("pwd", null);
      assertEquals(result6.type(), InterpreterResult.Type.TEXT);
      assertEquals(result6.message(), "/user");

      // Move a couple of levels and check we're in the right place

      InterpreterResult result7 = t.interpret("cd ../mr-history/done", null);
      assertEquals(result7.type(), InterpreterResult.Type.TEXT);
      assertEquals(result7.message(), "OK");

      InterpreterResult result8 = t.interpret("ls -l ", null);
      assertEquals(result8.type(), InterpreterResult.Type.TEXT);

      InterpreterResult result9 = t.interpret("ls -l /mr-history/done", null);
      assertEquals(result9.type(), InterpreterResult.Type.TEXT);

      assertEquals(result8.message(), result9.message());

      InterpreterResult result10 = t.interpret("cd ../..", null);
      assertEquals(result10.type(), InterpreterResult.Type.TEXT);
      assertEquals(result7.message(), "OK");

      InterpreterResult result11 = t.interpret("ls -l ", null);
      assertEquals(result11.type(), InterpreterResult.Type.TEXT);

      // we should be back to first result after all this navigation
      assertEquals(result1.message(), result11.message());

      t.close();
    }
  }

  /**
   * Store command results from curl against a real file system
   */
  class MockFileSystem {
    HashMap<String, String> mfs = new HashMap<String, String>();
    void addListStatusData() {
      mfs.put("/?op=LISTSTATUS",
          "{\"FileStatuses\":{\"FileStatus\":[\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16389,\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1438548219672,\"owner\":\"yarn\",\"pathSuffix\":\"app-logs\",\"permission\":\"777\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16395,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548030045,\"owner\":\"hdfs\",\"pathSuffix\":\"hdp\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16390,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547985336,\"owner\":\"mapred\",\"pathSuffix\":\"mapred\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":2,\"fileId\":16392,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547985346,\"owner\":\"hdfs\",\"pathSuffix\":\"mr-history\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16400,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548089725,\"owner\":\"hdfs\",\"pathSuffix\":\"system\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16386,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548150089,\"owner\":\"hdfs\",\"pathSuffix\":\"tmp\",\"permission\":\"777\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
              "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16387,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547921792,\"owner\":\"hdfs\",\"pathSuffix\":\"user\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n" +
              "]}}"
      );
      mfs.put("/user?op=LISTSTATUS",
         "{\"FileStatuses\":{\"FileStatus\":[\n" +
             "        {\"accessTime\":0,\"blockSize\":0,\"childrenNum\":4,\"fileId\":16388,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253161263,\"owner\":\"ambari-qa\",\"pathSuffix\":\"ambari-qa\",\"permission\":\"770\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n" +
             "        ]}}"
      );
      mfs.put("/tmp?op=LISTSTATUS",
          "{\"FileStatuses\":{\"FileStatus\":[\n" +
              "        {\"accessTime\":1441253097489,\"blockSize\":134217728,\"childrenNum\":0,\"fileId\":16400,\"group\":\"hdfs\",\"length\":1645,\"modificationTime\":1441253097517,\"owner\":\"hdfs\",\"pathSuffix\":\"ida8c06540_date040315\",\"permission\":\"755\",\"replication\":3,\"storagePolicy\":0,\"type\":\"FILE\"}\n" +
              "        ]}}"
      );
      mfs.put("/mr-history/done?op=LISTSTATUS",
          "{\"FileStatuses\":{\"FileStatus\":[\n" +
          "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16433,\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1441253197481,\"owner\":\"mapred\",\"pathSuffix\":\"2015\",\"permission\":\"770\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n" +
          "]}}"
      );
    }
    void addGetFileStatusData() {
      mfs.put("/?op=GETFILESTATUS",
          "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":7,\"fileId\":16385,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548089725,\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
      mfs.put("/user?op=GETFILESTATUS",
          "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16387,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253043188,\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"755\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
      mfs.put("/tmp?op=GETFILESTATUS",
          "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16386,\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253097489,\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"777\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
      mfs.put("/mr-history/done?op=GETFILESTATUS",
          "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16393,\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1441253197480,\"owner\":\"mapred\",\"pathSuffix\":\"\",\"permission\":\"777\",\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
    }
    public void addMockData(HDFSCommand.Op op) {
      if (op.op.equals("LISTSTATUS")) {
        addListStatusData();
      } else if (op.op.equals("GETFILESTATUS")) {
        addGetFileStatusData();
      }
      // do nothing
    }
    public String get(String key) {
      return mfs.get(key);
    }
  }

  /**
   * Run commands against mock file system that simulates webhdfs responses
   */
  class MockHDFSCommand extends HDFSCommand {
    MockFileSystem fs = null;

    public MockHDFSCommand(String url, String user, Logger logger) {
      super(url, user, logger, 1000);
      fs = new MockFileSystem();
      fs.addMockData(getFileStatus);
      fs.addMockData(listStatus);
    }

    @Override
    public String runCommand(Op op, String path, Arg[] args) throws Exception {

      String error = checkArgs(op, path, args);
      assertNull(error);

      String c = path + "?op=" + op.op;

      if (args != null) {
        for (Arg a : args) {
          c += "&" + a.key + "=" + a.value;
        }
      }
      return fs.get(c);
    }
  }

  /**
   * Mock Interpreter - uses Mock HDFS command
   */
  class MockHDFSFileInterpreter extends HDFSFileInterpreter {

    @Override
    public void prepare() {
      // Run commands against mock File System instead of WebHDFS
      cmd = new MockHDFSCommand("", "", logger);
      gson = new Gson();
    }

    public MockHDFSFileInterpreter(Properties property) {
      super(property);
    }

}