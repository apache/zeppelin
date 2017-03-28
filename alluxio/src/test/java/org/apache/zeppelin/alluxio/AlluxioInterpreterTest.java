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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import alluxio.client.WriteType;
import alluxio.client.file.URIStatus;

import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.junit.*;

import alluxio.Constants;
import alluxio.AlluxioURI;
import alluxio.client.FileSystemTestUtils;
import alluxio.client.file.FileInStream;
import alluxio.client.file.FileSystem;
import alluxio.exception.ExceptionMessage;
import alluxio.exception.AlluxioException;
import alluxio.master.LocalAlluxioCluster;
import alluxio.shell.command.CommandUtils;
import alluxio.util.FormatUtils;
import alluxio.util.io.BufferUtils;
import alluxio.util.io.PathUtils;


public class AlluxioInterpreterTest {
  private AlluxioInterpreter alluxioInterpreter;
  private static final int SIZE_BYTES = Constants.MB * 10;
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
    mLocalAlluxioCluster = new LocalAlluxioCluster(SIZE_BYTES, 1000);
    mLocalAlluxioCluster.start();
    fs = mLocalAlluxioCluster.getClient();

    final Properties props = new Properties();
    props.put(AlluxioInterpreter.ALLUXIO_MASTER_HOSTNAME, mLocalAlluxioCluster.getMasterHostname());
    props.put(AlluxioInterpreter.ALLUXIO_MASTER_PORT, mLocalAlluxioCluster.getMasterPort() + "");
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
      new InterpreterCompletion("copyFromLocal", "copyFromLocal", CompletionType.command.name()),
      new InterpreterCompletion("copyToLocal", "copyToLocal", CompletionType.command.name()),
      new InterpreterCompletion("count", "count", CompletionType.command.name()));
    List expectedResultThree = Arrays.asList(
      new InterpreterCompletion("copyFromLocal", "copyFromLocal", CompletionType.command.name()),
      new InterpreterCompletion("copyToLocal", "copyToLocal", CompletionType.command.name()));
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
  public void catDirectoryTest() throws IOException {
    String expected = "Successfully created directory /testDir\n\n" +
            "Path /testDir must be a file\n";

    InterpreterResult output = alluxioInterpreter.interpret("mkdir /testDir" +
            "\ncat /testDir", null);

    Assert.assertEquals(Code.ERROR, output.code());
    Assert.assertEquals(expected, output.message().get(0).getData());
  }

  @Test
  public void catNotExistTest() throws IOException {
    InterpreterResult output = alluxioInterpreter.interpret("cat /testFile", null);
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void catTest() throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WriteType.MUST_CACHE,
            10, 10);
    InterpreterResult output = alluxioInterpreter.interpret("cat /testFile", null);

    byte[] expected = BufferUtils.getIncreasingByteArray(10);

    Assert.assertEquals(Code.SUCCESS, output.code());
    Assert.assertArrayEquals(expected,
            output.message().get(0).getData().substring(0, output.message().get(0).getData().length() - 1).getBytes());
  }

  @Test
  public void copyFromLocalLargeTest() throws IOException, AlluxioException {
    File testFile = new File(mLocalAlluxioCluster.getAlluxioHome() + "/testFile");
    testFile.createNewFile();
    FileOutputStream fos = new FileOutputStream(testFile);
    byte[] toWrite = BufferUtils.getIncreasingByteArray(SIZE_BYTES);
    fos.write(toWrite);
    fos.close();

    InterpreterResult output = alluxioInterpreter.interpret("copyFromLocal " +
            testFile.getAbsolutePath() + " /testFile", null);
    Assert.assertEquals(
            "Copied " + testFile.getAbsolutePath() + " to /testFile\n\n",
            output.message().get(0).getData());

    long fileLength = fs.getStatus(new AlluxioURI("/testFile")).getLength();
    Assert.assertEquals(SIZE_BYTES, fileLength);

    FileInStream fStream = fs.openFile(new AlluxioURI("/testFile"));
    byte[] read = new byte[SIZE_BYTES];
    fStream.read(read);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(SIZE_BYTES, read));
  }

  @Test
  public void loadFileTest() throws IOException, AlluxioException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WriteType.CACHE_THROUGH, 10, 10);

    int memPercentage = fs.getStatus(new AlluxioURI("/testFile")).getInMemoryPercentage();
    Assert.assertFalse(memPercentage == 0);

    alluxioInterpreter.interpret("load /testFile", null);

    memPercentage = fs.getStatus(new AlluxioURI("/testFile")).getInMemoryPercentage();
    Assert.assertTrue(memPercentage == 100);
  }

  @Test
  public void loadDirTest() throws IOException, AlluxioException {
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA", WriteType.CACHE_THROUGH, 10, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileB", WriteType.MUST_CACHE, 10, 10);

    int memPercentageA = fs.getStatus(new AlluxioURI("/testRoot/testFileA")).getInMemoryPercentage();
    int memPercentageB = fs.getStatus(new AlluxioURI("/testRoot/testFileB")).getInMemoryPercentage();
    Assert.assertFalse(memPercentageA == 0);
    Assert.assertTrue(memPercentageB == 100);

    alluxioInterpreter.interpret("load /testRoot", null);

    memPercentageA = fs.getStatus(new AlluxioURI("/testRoot/testFileA")).getInMemoryPercentage();
    memPercentageB = fs.getStatus(new AlluxioURI("/testRoot/testFileB")).getInMemoryPercentage();
    Assert.assertTrue(memPercentageA == 100);
    Assert.assertTrue(memPercentageB == 100);
  }

  @Test
  public void copyFromLocalTest() throws IOException, AlluxioException {
    File testDir = new File(mLocalAlluxioCluster.getAlluxioHome() + "/testDir");
    testDir.mkdir();
    File testDirInner = new File(mLocalAlluxioCluster.getAlluxioHome() + "/testDir/testDirInner");
    testDirInner.mkdir();
    File testFile =
            generateFileContent("/testDir/testFile", BufferUtils.getIncreasingByteArray(10));

    generateFileContent("/testDir/testDirInner/testFile2",
            BufferUtils.getIncreasingByteArray(10, 20));

    InterpreterResult output = alluxioInterpreter.interpret("copyFromLocal " +
            testFile.getParent() + " /testDir", null);
    Assert.assertEquals(
            "Copied " + testFile.getParent() + " to /testDir\n\n",
            output.message().get(0).getData());

    long fileLength1 = fs.getStatus(new AlluxioURI("/testDir/testFile")).getLength();
    long fileLength2 = fs.getStatus(new AlluxioURI("/testDir/testDirInner/testFile2")).getLength();
    Assert.assertEquals(10, fileLength1);
    Assert.assertEquals(20, fileLength2);

    FileInStream fStream1 = fs.openFile(new AlluxioURI("/testDir/testFile"));
    FileInStream fStream2 = fs.openFile(new AlluxioURI("/testDir/testDirInner/testFile2"));
    byte[] read = new byte[10];
    fStream1.read(read);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, read));
    read = new byte[20];
    fStream2.read(read);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, 20, read));
  }

  @Test
  public void copyFromLocalTestWithFullURI() throws IOException, AlluxioException {
    File testFile = generateFileContent("/srcFileURI", BufferUtils.getIncreasingByteArray(10));
    String uri = "tachyon://" + mLocalAlluxioCluster.getMasterHostname() + ":"
            + mLocalAlluxioCluster.getMasterPort() + "/destFileURI";

    InterpreterResult output = alluxioInterpreter.interpret("copyFromLocal " +
            testFile.getPath() + " " + uri, null);
    Assert.assertEquals(
            "Copied " + testFile.getPath() + " to " + uri + "\n\n",
            output.message().get(0).getData());

    long fileLength = fs.getStatus(new AlluxioURI("/destFileURI")).getLength();
    Assert.assertEquals(10L, fileLength);

    FileInStream fStream = fs.openFile(new AlluxioURI("/destFileURI"));
    byte[] read = new byte[10];
    fStream.read(read);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, read));
  }

  @Test
  public void copyFromLocalFileToDstPathTest() throws IOException, AlluxioException {
    String dataString = "copyFromLocalFileToDstPathTest";
    byte[] data = dataString.getBytes();
    File localDir = new File(mLocalAlluxioCluster.getAlluxioHome() + "/localDir");
    localDir.mkdir();
    File localFile = generateFileContent("/localDir/testFile", data);

    alluxioInterpreter.interpret("mkdir /dstDir", null);
    alluxioInterpreter.interpret("copyFromLocal " + localFile.getPath() + " /dstDir", null);

    FileInStream fStream = fs.openFile(new AlluxioURI("/dstDir/testFile"));
    long fileLength = fs.getStatus(new AlluxioURI("/dstDir/testFile")).getLength();

    byte[] read = new byte[(int) fileLength];
    fStream.read(read);
    Assert.assertEquals(new String(read), dataString);
  }

  @Test
  public void copyToLocalLargeTest() throws IOException {
    copyToLocalWithBytes(SIZE_BYTES);
  }

  @Test
  public void copyToLocalTest() throws IOException {
    copyToLocalWithBytes(10);
  }

  private void copyToLocalWithBytes(int bytes) throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testFile", WriteType.MUST_CACHE, 10, 10);

    InterpreterResult output = alluxioInterpreter.interpret("copyToLocal /testFile " +
            mLocalAlluxioCluster.getAlluxioHome() + "/testFile", null);

    Assert.assertEquals(
            "Copied /testFile to " + mLocalAlluxioCluster.getAlluxioHome() + "/testFile\n\n",
            output.message().get(0).getData());
    fileReadTest("/testFile", 10);
  }

  @Test
  public void countNotExistTest() throws IOException {
    InterpreterResult output = alluxioInterpreter.interpret("count /NotExistFile", null);
    Assert.assertEquals(Code.ERROR, output.code());
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message().get(0).getData());
  }

  @Test
  public void countTest() throws IOException {
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA",
            WriteType.CACHE_THROUGH, 10, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testDir/testFileB",
            WriteType.CACHE_THROUGH, 20, 20);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileB",
            WriteType.CACHE_THROUGH, 30, 30);

    InterpreterResult output = alluxioInterpreter.interpret("count /testRoot", null);

    String expected = "";
    String format = "%-25s%-25s%-15s\n";
    expected += String.format(format, "File Count", "Folder Count", "Total Bytes");
    expected += String.format(format, 3, 2, 60);
    expected += "\n";
    Assert.assertEquals(expected, output.message().get(0).getData());
  }

  @Test
  public void fileinfoNotExistTest() throws IOException {
    InterpreterResult output = alluxioInterpreter.interpret("fileInfo /NotExistFile", null);
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message().get(0).getData());
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void locationNotExistTest() throws IOException {
    InterpreterResult output = alluxioInterpreter.interpret("location /NotExistFile", null);
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message().get(0).getData());
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void lsTest() throws IOException, AlluxioException {
    URIStatus[] files = new URIStatus[3];

    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA",
            WriteType.MUST_CACHE, 10, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testDir/testFileB",
            WriteType.MUST_CACHE, 20, 20);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileC",
            WriteType.THROUGH, 30, 30);

    files[0] = fs.getStatus(new AlluxioURI("/testRoot/testFileA"));
    files[1] = fs.getStatus(new AlluxioURI("/testRoot/testDir"));
    files[2] = fs.getStatus(new AlluxioURI("/testRoot/testFileC"));

    InterpreterResult output = alluxioInterpreter.interpret("ls /testRoot", null);

    String expected = "";
    String format = "%-10s%-25s%-15s%-5s\n";
    expected += String.format(format, FormatUtils.getSizeFromBytes(10),
            CommandUtils.convertMsToDate(files[0].getCreationTimeMs()), "In Memory", "/testRoot/testFileA");
    expected += String.format(format, FormatUtils.getSizeFromBytes(0),
            CommandUtils.convertMsToDate(files[1].getCreationTimeMs()), "", "/testRoot/testDir");
    expected += String.format(format, FormatUtils.getSizeFromBytes(30),
            CommandUtils.convertMsToDate(files[2].getCreationTimeMs()), "Not In Memory",
            "/testRoot/testFileC");
    expected += "\n";

    Assert.assertEquals(Code.SUCCESS, output.code());
    Assert.assertEquals(expected, output.message().get(0).getData());
  }

  @Test
  public void lsRecursiveTest() throws IOException, AlluxioException {
    URIStatus[] files = new URIStatus[4];

    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileA",
            WriteType.MUST_CACHE, 10, 10);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testDir/testFileB",
            WriteType.MUST_CACHE, 20, 20);
    FileSystemTestUtils.createByteFile(fs, "/testRoot/testFileC",
            WriteType.THROUGH, 30, 30);

    files[0] = fs.getStatus(new AlluxioURI("/testRoot/testFileA"));
    files[1] = fs.getStatus(new AlluxioURI("/testRoot/testDir"));
    files[2] = fs.getStatus(new AlluxioURI("/testRoot/testDir/testFileB"));
    files[3] = fs.getStatus(new AlluxioURI("/testRoot/testFileC"));

    InterpreterResult output = alluxioInterpreter.interpret("ls -R /testRoot", null);

    String expected = "";
    String format = "%-10s%-25s%-15s%-5s\n";
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(10),
                    CommandUtils.convertMsToDate(files[0].getCreationTimeMs()), "In Memory",
                    "/testRoot/testFileA");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(0),
                    CommandUtils.convertMsToDate(files[1].getCreationTimeMs()), "", "/testRoot/testDir");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(20),
                    CommandUtils.convertMsToDate(files[2].getCreationTimeMs()), "In Memory",
                    "/testRoot/testDir/testFileB");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(30),
                    CommandUtils.convertMsToDate(files[3].getCreationTimeMs()), "Not In Memory",
                    "/testRoot/testFileC");
    expected += "\n";

    Assert.assertEquals(expected, output.message().get(0).getData());
  }

  @Test
  public void mkdirComplexPathTest() throws IOException, AlluxioException {
    InterpreterResult output = alluxioInterpreter.interpret(
            "mkdir /Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File", null);

    boolean existsDir = fs.exists(new AlluxioURI("/Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File"));
    Assert.assertEquals(
            "Successfully created directory /Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File\n\n",
            output.message().get(0).getData());
    Assert.assertTrue(existsDir);
  }

  @Test
  public void mkdirExistingTest() throws IOException {
    String command = "mkdir /festFile1";
    Assert.assertEquals(Code.SUCCESS, alluxioInterpreter.interpret(command, null).code());
    Assert.assertEquals(Code.ERROR, alluxioInterpreter.interpret(command, null).code());
  }

  @Test
  public void mkdirInvalidPathTest() throws IOException {
    Assert.assertEquals(
            Code.ERROR,
            alluxioInterpreter.interpret("mkdir /test File Invalid Path", null).code());
  }

  @Test
  public void mkdirShortPathTest() throws IOException, AlluxioException {
    InterpreterResult output = alluxioInterpreter.interpret("mkdir /root/testFile1", null);
    boolean existsDir = fs.exists(new AlluxioURI("/root/testFile1"));
    Assert.assertEquals(
            "Successfully created directory /root/testFile1\n\n",
            output.message().get(0).getData());
    Assert.assertTrue(existsDir);
  }

  @Test
  public void mkdirTest() throws IOException, AlluxioException {
    String qualifiedPath =
            "tachyon://" + mLocalAlluxioCluster.getMasterHostname() + ":"
                    + mLocalAlluxioCluster.getMasterPort() + "/root/testFile1";
    InterpreterResult output = alluxioInterpreter.interpret("mkdir " + qualifiedPath, null);
    boolean existsDir = fs.exists(new AlluxioURI("/root/testFile1"));
    Assert.assertEquals(
            "Successfully created directory " + qualifiedPath + "\n\n",
            output.message().get(0).getData());
    Assert.assertTrue(existsDir);
  }

  private File generateFileContent(String path, byte[] toWrite)
          throws IOException {
    File testFile = new File(mLocalAlluxioCluster.getAlluxioHome() + path);
    testFile.createNewFile();
    FileOutputStream fos = new FileOutputStream(testFile);
    fos.write(toWrite);
    fos.close();
    return testFile;
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