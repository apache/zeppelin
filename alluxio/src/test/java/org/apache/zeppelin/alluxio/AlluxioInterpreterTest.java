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

package org.apache.zeppelin.alluxio;


import alluxio.conf.Configuration;
import alluxio.grpc.WritePType;
import alluxio.client.file.FileSystemTestUtils;
import alluxio.master.LocalAlluxioCluster;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import alluxio.AlluxioURI;
import alluxio.client.file.FileSystem;
import alluxio.client.file.URIStatus;
import alluxio.exception.AlluxioException;
import alluxio.util.io.BufferUtils;
import alluxio.util.io.PathUtils;

import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

import static alluxio.cli.fs.command.CountCommand.COUNT_FORMAT;
import static org.junit.Assert.assertEquals;

public class AlluxioInterpreterTest {
  private AlluxioInterpreter alluxioInterpreter;
  private LocalAlluxioCluster mLocalAlluxioCluster = null;
  private FileSystem fs = null;

  @After
  public final void after() throws Exception {
    if (alluxioInterpreter != null) {
      alluxioInterpreter.close();
    }

    mLocalAlluxioCluster.stop();
  }

  @Before
  public final void before() throws Exception {
    mLocalAlluxioCluster = new LocalAlluxioCluster(1, false);
    mLocalAlluxioCluster.initConfiguration("alluxio-test");
    Configuration.global().validate();
    mLocalAlluxioCluster.start();

    fs = mLocalAlluxioCluster.getClient();

    final Properties props = new Properties();
    props.put(AlluxioInterpreter.ALLUXIO_MASTER_HOSTNAME, mLocalAlluxioCluster.getHostname());
    props.put(AlluxioInterpreter.ALLUXIO_MASTER_PORT, mLocalAlluxioCluster.getMasterRpcPort() + "");
    alluxioInterpreter = new AlluxioInterpreter(props);
    alluxioInterpreter.open();
  }

  @Test
  public void testCompletion() {
    List expectedResultOne = Arrays.asList(
        new InterpreterCompletion("cat", "cat", CompletionType.command.name()),
        new InterpreterCompletion("chgrp", "chgrp", CompletionType.command.name()),
        new InterpreterCompletion("chmod", "chmod", CompletionType.command.name()),
        new InterpreterCompletion("chown", "chown", CompletionType.command.name()),
        new InterpreterCompletion("copyFromLocal", "copyFromLocal", CompletionType.command.name()),
        new InterpreterCompletion("copyToLocal", "copyToLocal", CompletionType.command.name()),
        new InterpreterCompletion("count", "count", CompletionType.command.name()),
        new InterpreterCompletion("createLineage", "createLineage", CompletionType.command.name()));
    List expectedResultTwo = Arrays.asList(
        new InterpreterCompletion("copyFromLocal", "copyFromLocal",
              CompletionType.command.name()),
        new InterpreterCompletion("copyToLocal", "copyToLocal",
              CompletionType.command.name()),
        new InterpreterCompletion("count", "count", CompletionType.command.name()));
    List expectedResultThree = Arrays.asList(
        new InterpreterCompletion("copyFromLocal", "copyFromLocal",
              CompletionType.command.name()),
        new InterpreterCompletion("copyToLocal", "copyToLocal",
              CompletionType.command.name()));
    List expectedResultNone = new ArrayList<>();

    List<InterpreterCompletion> resultOne = alluxioInterpreter.completion("c", 0, null);
    List<InterpreterCompletion> resultTwo = alluxioInterpreter.completion("co", 0, null);
    List<InterpreterCompletion> resultThree = alluxioInterpreter.completion("copy", 0, null);
    List<InterpreterCompletion> resultNotMatch = alluxioInterpreter.completion("notMatch", 0, null);
    List<InterpreterCompletion> resultAll = alluxioInterpreter.completion("", 0, null);

    Assert.assertEquals(expectedResultOne, resultOne);
    Assert.assertEquals(expectedResultTwo, resultTwo);
    Assert.assertEquals(expectedResultThree, resultThree);
    Assert.assertEquals(expectedResultNone, resultNotMatch);

    List allCompletionList = new ArrayList<>();
    for (InterpreterCompletion ic : resultAll) {
      allCompletionList.add(ic.getName());
    }
    Assert.assertEquals(alluxioInterpreter.keywords, allCompletionList);
  }

  @Test
  public void catTest() throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WritePType.MUST_CACHE, 10, 10);
    InterpreterResult output = alluxioInterpreter.interpret("cat /testFile", null);

    byte[] expected = BufferUtils.getIncreasingByteArray(10);

    Assert.assertEquals(Code.SUCCESS, output.code());
    Assert.assertArrayEquals(expected,
            output.message().get(0).getData().substring(0,
                    output.message().get(0).getData().length() - 1).getBytes());
  }

  @Test
  public void loadFileTest() throws IOException, AlluxioException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WritePType.CACHE_THROUGH, 10, 10);

    int memPercentage = fs.getStatus(new AlluxioURI("/testFile")).getInMemoryPercentage();
    Assert.assertFalse(memPercentage == 0);

    alluxioInterpreter.interpret("load /testFile", null);

    memPercentage = fs.getStatus(new AlluxioURI("/testFile")).getInMemoryPercentage();
    Assert.assertTrue(memPercentage == 100);
  }

  @Test
  public void copyToLocalTest() throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WritePType.MUST_CACHE, 10, 10);

    InterpreterResult output = alluxioInterpreter.interpret("copyToLocal /testFile " +
            mLocalAlluxioCluster.getAlluxioHome() + "/testFile", null);

    Assert.assertEquals(
            "Copied /testFile to file://" + mLocalAlluxioCluster.getAlluxioHome() + "/testFile\n\n",
            output.message().get(0).getData());
    fileReadTest("/testFile", 10);
  }

  @Test
  public void countTest() throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA",
            WritePType.MUST_CACHE, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testDir/testFileB",
            WritePType.MUST_CACHE, 20);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileB",
            WritePType.MUST_CACHE, 30);

    InterpreterResult output = alluxioInterpreter.interpret("count /testRoot", null);

    String expected = "";
    expected += String.format(COUNT_FORMAT, "File Count", "Folder Count", "Folder Size");
    expected += String.format(COUNT_FORMAT, 3, 1, 60);
    expected += "\n";
    Assert.assertEquals(expected, output.message().get(0).getData());

    InterpreterResult output2 = alluxioInterpreter.interpret("count -h /testRoot", null);
    String expected2 = "";
    expected2 += String.format(COUNT_FORMAT, "File Count", "Folder Count", "Folder Size");
    expected2 += String.format(COUNT_FORMAT, 3, 1, "60B");
    expected2 += "\n";
    assertEquals(expected2, output2.message().get(0).getData());
  }

  @Test
  public void lsTest() throws IOException, AlluxioException {
    URIStatus[] files = new URIStatus[3];

    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA",
            WritePType.MUST_CACHE, 10, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testDir/testFileB",
            WritePType.MUST_CACHE, 20, 20);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileC",
            WritePType.THROUGH, 30, 30);

    files[0] = fs.getStatus(new AlluxioURI("/testRoot/testFileA"));
    files[1] = fs.getStatus(new AlluxioURI("/testRoot/testDir"));
    files[2] = fs.getStatus(new AlluxioURI("/testRoot/testFileC"));

    InterpreterResult output = alluxioInterpreter.interpret("ls /testRoot", null);

    Assert.assertEquals(Code.SUCCESS, output.code());
  }

  @Test
  public void mkdirTest() throws IOException, AlluxioException {
    String qualifiedPath =
            "alluxio://" + mLocalAlluxioCluster.getHostname() + ":"
                    + mLocalAlluxioCluster.getMasterRpcPort() + "/root/testFile1";
    InterpreterResult output = alluxioInterpreter.interpret("mkdir " + qualifiedPath, null);
    boolean existsDir = fs.exists(new AlluxioURI("/root/testFile1"));
    Assert.assertEquals(
            "Successfully created directory " + qualifiedPath + "\n\n",
            output.message().get(0).getData());
    Assert.assertTrue(existsDir);
  }

  private void fileReadTest(String fileName, int size) throws IOException {
    File testFile = new File(PathUtils.concatPath(mLocalAlluxioCluster.getAlluxioHome(), fileName));
    FileInputStream fis = new FileInputStream(testFile);
    byte[] read = new byte[size];
    fis.read(read);
    fis.close();
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(size, read));
  }
}
