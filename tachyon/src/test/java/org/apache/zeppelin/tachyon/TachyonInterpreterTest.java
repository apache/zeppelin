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

package org.apache.zeppelin.tachyon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.*;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.client.TachyonFSTestUtils;
import tachyon.client.TachyonStorageType;
import tachyon.client.UnderStorageType;
import tachyon.client.file.FileInStream;
import tachyon.client.file.TachyonFile;
import tachyon.client.file.TachyonFileSystem;
import tachyon.client.file.options.InStreamOptions;
import tachyon.conf.TachyonConf;
import tachyon.exception.ExceptionMessage;
import tachyon.exception.TachyonException;
import tachyon.master.LocalTachyonCluster;
import tachyon.shell.TfsShell;
import tachyon.thrift.FileInfo;
import tachyon.util.FormatUtils;
import tachyon.util.io.BufferUtils;
import tachyon.util.io.PathUtils;


public class TachyonInterpreterTest {
  private TachyonInterpreter tachyonInterpreter;
  private static final int SIZE_BYTES = Constants.MB * 10;
  private LocalTachyonCluster mLocalTachyonCluster = null;
  private TachyonFileSystem mTfs = null;

  @After
  public final void after() throws Exception {
    if (tachyonInterpreter != null) {
      tachyonInterpreter.close();
    }
    mLocalTachyonCluster.stop();
  }

  @Before
  public final void before() throws Exception {
    mLocalTachyonCluster = new LocalTachyonCluster(SIZE_BYTES, 1000, Constants.GB);
    mLocalTachyonCluster.start();
    mTfs = mLocalTachyonCluster.getClient();

    final Properties props = new Properties();
    props.put(TachyonInterpreter.TACHYON_MASTER_HOSTNAME, mLocalTachyonCluster.getMasterHostname());
    props.put(TachyonInterpreter.TACHYON_MASTER_PORT, mLocalTachyonCluster.getMasterPort() + "");
    tachyonInterpreter = new TachyonInterpreter(props);
    tachyonInterpreter.open();
  }

  @Test
  public void testCompletion() {
    List<String> expectedResultOne = Arrays.asList("cat", "copyFromLocal",
            "copyToLocal", "count");
    List<String> expectedResultTwo = Arrays.asList("copyFromLocal",
            "copyToLocal", "count");
    List<String> expectedResultThree = Arrays.asList("copyFromLocal", "copyToLocal");
    List<String> expectedResultNone = new ArrayList<String>();

    List<String> resultOne = tachyonInterpreter.completion("c", 0);
    List<String> resultTwo = tachyonInterpreter.completion("co", 0);
    List<String> resultThree = tachyonInterpreter.completion("copy", 0);
    List<String> resultNotMatch = tachyonInterpreter.completion("notMatch", 0);
    List<String> resultAll = tachyonInterpreter.completion("", 0);

    Assert.assertEquals(expectedResultOne, resultOne);
    Assert.assertEquals(expectedResultTwo, resultTwo);
    Assert.assertEquals(expectedResultThree, resultThree);
    Assert.assertEquals(expectedResultNone, resultNotMatch);
    Assert.assertEquals(tachyonInterpreter.keywords, resultAll);
  }

  @Test
  public void catDirectoryTest() throws IOException {
    String expected = "Successfully created directory /testDir\n\n" +
            "/testDir is not a file.\n";

    InterpreterResult output = tachyonInterpreter.interpret("mkdir /testDir" +
            "\ncat /testDir", null);

    Assert.assertEquals(Code.ERROR, output.code());
    Assert.assertEquals(expected, output.message());
  }

  @Test
  public void catNotExistTest() throws IOException {
    InterpreterResult output = tachyonInterpreter.interpret("cat /testFile", null);
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void catTest() throws IOException {
    TachyonFSTestUtils.createByteFile(mTfs, "/testFile", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 10);
    InterpreterResult output = tachyonInterpreter.interpret("cat /testFile", null);

    byte[] expected = BufferUtils.getIncreasingByteArray(10);

    Assert.assertEquals(Code.SUCCESS, output.code());
    Assert.assertArrayEquals(expected,
            output.message().substring(0, output.message().length() - 1).getBytes());
  }

  @Test
  public void copyFromLocalLargeTest() throws IOException, TachyonException {
    File testFile = new File(mLocalTachyonCluster.getTachyonHome() + "/testFile");
    testFile.createNewFile();
    FileOutputStream fos = new FileOutputStream(testFile);
    byte[] toWrite = BufferUtils.getIncreasingByteArray(SIZE_BYTES);
    fos.write(toWrite);
    fos.close();

    InterpreterResult output = tachyonInterpreter.interpret("copyFromLocal " +
            testFile.getAbsolutePath() + " /testFile", null);
    Assert.assertEquals(
            "Copied " + testFile.getAbsolutePath() + " to /testFile\n\n",
            output.message());

    TachyonFile tFile = mTfs.open(new TachyonURI("/testFile"));
    FileInfo fileInfo = mTfs.getInfo(tFile);
    Assert.assertNotNull(fileInfo);
    Assert.assertEquals(SIZE_BYTES, fileInfo.length);

    InStreamOptions options =
            new InStreamOptions.Builder(new TachyonConf()).setTachyonStorageType(
                    TachyonStorageType.NO_STORE).build();
    FileInStream tfis = mTfs.getInStream(tFile, options);
    byte[] read = new byte[SIZE_BYTES];
    tfis.read(read);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(SIZE_BYTES, read));
  }

  @Test
  public void loadFileTest() throws IOException, TachyonException {
    TachyonFile file =
            TachyonFSTestUtils.createByteFile(mTfs, "/testFile", TachyonStorageType.NO_STORE,
                    UnderStorageType.SYNC_PERSIST, 10);
    FileInfo fileInfo = mTfs.getInfo(file);
    Assert.assertFalse(fileInfo.getInMemoryPercentage() == 100);

    tachyonInterpreter.interpret("load /testFile", null);

    fileInfo = mTfs.getInfo(file);
    Assert.assertTrue(fileInfo.getInMemoryPercentage() == 100);
  }

  @Test
  public void loadDirTest() throws IOException, TachyonException {
    TachyonFile fileA = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileA",
            TachyonStorageType.NO_STORE, UnderStorageType.SYNC_PERSIST, 10);
    TachyonFile fileB = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileB",
            TachyonStorageType.STORE, UnderStorageType.NO_PERSIST, 10);
    FileInfo fileInfoA = mTfs.getInfo(fileA);
    FileInfo fileInfoB = mTfs.getInfo(fileB);
    Assert.assertFalse(fileInfoA.getInMemoryPercentage() == 100);
    Assert.assertTrue(fileInfoB.getInMemoryPercentage() == 100);

    tachyonInterpreter.interpret("load /testRoot", null);

    fileInfoA = mTfs.getInfo(fileA);
    fileInfoB = mTfs.getInfo(fileB);
    Assert.assertTrue(fileInfoA.getInMemoryPercentage() == 100);
    Assert.assertTrue(fileInfoB.getInMemoryPercentage() == 100);
  }

  @Test
  public void copyFromLocalTest() throws IOException, TachyonException {
    File testDir = new File(mLocalTachyonCluster.getTachyonHome() + "/testDir");
    testDir.mkdir();
    File testDirInner = new File(mLocalTachyonCluster.getTachyonHome() + "/testDir/testDirInner");
    testDirInner.mkdir();
    File testFile =
            generateFileContent("/testDir/testFile", BufferUtils.getIncreasingByteArray(10));

    generateFileContent("/testDir/testDirInner/testFile2",
            BufferUtils.getIncreasingByteArray(10, 20));

    InterpreterResult output = tachyonInterpreter.interpret("copyFromLocal " +
            testFile.getParent() + " /testDir", null);
    Assert.assertEquals(
            "Copied " + testFile.getParent() + " to /testDir\n\n",
            output.message());

    TachyonFile file1 = mTfs.open(new TachyonURI("/testDir/testFile"));
    TachyonFile file2 = mTfs.open(new TachyonURI("/testDir/testDirInner/testFile2"));
    FileInfo fileInfo1 = mTfs.getInfo(file1);
    FileInfo fileInfo2 = mTfs.getInfo(file2);
    Assert.assertNotNull(fileInfo1);
    Assert.assertNotNull(fileInfo2);
    Assert.assertEquals(10, fileInfo1.length);
    Assert.assertEquals(20, fileInfo2.length);

    byte[] read = readContent(file1, 10);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, read));
    read = readContent(file2, 20);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, 20, read));
  }

  @Test
  public void copyFromLocalTestWithFullURI() throws IOException, TachyonException {
    File testFile = generateFileContent("/srcFileURI", BufferUtils.getIncreasingByteArray(10));
    String tachyonURI = "tachyon://" + mLocalTachyonCluster.getMasterHostname() + ":"
            + mLocalTachyonCluster.getMasterPort() + "/destFileURI";

    InterpreterResult output = tachyonInterpreter.interpret("copyFromLocal " +
            testFile.getPath() + " " + tachyonURI, null);
    Assert.assertEquals(
            "Copied " + testFile.getPath() + " to " + tachyonURI + "\n\n",
            output.message());

    TachyonFile file = mTfs.open(new TachyonURI("/destFileURI"));
    FileInfo fileInfo = mTfs.getInfo(file);
    Assert.assertEquals(10L, fileInfo.length);
    byte[] read = readContent(file, 10);
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(10, read));
  }

  @Test
  public void copyFromLocalFileToDstPathTest() throws IOException, TachyonException {
    String dataString = "copyFromLocalFileToDstPathTest";
    byte[] data = dataString.getBytes();
    File localDir = new File(mLocalTachyonCluster.getTachyonHome() + "/localDir");
    localDir.mkdir();
    File localFile = generateFileContent("/localDir/testFile", data);

    tachyonInterpreter.interpret("mkdir /dstDir", null);
    tachyonInterpreter.interpret("copyFromLocal " + localFile.getPath() + " /dstDir", null);

    TachyonFile file = mTfs.open(new TachyonURI("/dstDir/testFile"));
    FileInfo fileInfo = mTfs.getInfo(file);
    Assert.assertNotNull(fileInfo);
    byte[] read = readContent(file, data.length);
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
    TachyonFSTestUtils.createByteFile(mTfs, "/testFile", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, bytes);

    InterpreterResult output = tachyonInterpreter.interpret("copyToLocal /testFile " +
            mLocalTachyonCluster.getTachyonHome() + "/testFile", null);

    Assert.assertEquals(
            "Copied /testFile to " + mLocalTachyonCluster.getTachyonHome() + "/testFile\n\n",
            output.message());
    fileReadTest("/testFile", 10);
  }

  @Test
  public void countNotExistTest() throws IOException {
    InterpreterResult output = tachyonInterpreter.interpret("count /NotExistFile", null);
    Assert.assertEquals(Code.ERROR, output.code());
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message());
  }

  @Test
  public void countTest() throws IOException {
    TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileA", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 10);
    TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testDir/testFileB", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 20);
    TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileB", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 30);

    InterpreterResult output = tachyonInterpreter.interpret("count /testRoot", null);

    String expected = "";
    String format = "%-25s%-25s%-15s\n";
    expected += String.format(format, "File Count", "Folder Count", "Total Bytes");
    expected += String.format(format, 3, 2, 60);
    expected += "\n";
    Assert.assertEquals(expected, output.message());
  }

  @Test
  public void fileinfoNotExistTest() throws IOException {
    InterpreterResult output = tachyonInterpreter.interpret("fileinfo /NotExistFile", null);
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message());
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void locationNotExistTest() throws IOException {
    InterpreterResult output = tachyonInterpreter.interpret("location /NotExistFile", null);
    Assert.assertEquals(ExceptionMessage.PATH_DOES_NOT_EXIST.getMessage("/NotExistFile") + "\n",
            output.message());
    Assert.assertEquals(Code.ERROR, output.code());
  }

  @Test
  public void lsTest() throws IOException, TachyonException {
    FileInfo[] files = new FileInfo[3];

    TachyonFile fileA = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileA",
            TachyonStorageType.STORE, UnderStorageType.NO_PERSIST, 10);
    files[0] = mTfs.getInfo(fileA);
    TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testDir/testFileB", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 20);
    files[1] = mTfs.getInfo(mTfs.open(new TachyonURI("/testRoot/testDir")));
    TachyonFile fileC = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileC",
            TachyonStorageType.NO_STORE, UnderStorageType.SYNC_PERSIST, 30);
    files[2] = mTfs.getInfo(fileC);

    InterpreterResult output = tachyonInterpreter.interpret("ls /testRoot", null);

    String expected = "";
    String format = "%-10s%-25s%-15s%-5s\n";
    expected += String.format(format, FormatUtils.getSizeFromBytes(10),
            TfsShell.convertMsToDate(files[0].getCreationTimeMs()), "In Memory", "/testRoot/testFileA");
    expected += String.format(format, FormatUtils.getSizeFromBytes(0),
            TfsShell.convertMsToDate(files[1].getCreationTimeMs()), "", "/testRoot/testDir");
    expected += String.format(format, FormatUtils.getSizeFromBytes(30),
            TfsShell.convertMsToDate(files[2].getCreationTimeMs()), "Not In Memory",
            "/testRoot/testFileC");
    expected += "\n";

    Assert.assertEquals(Code.SUCCESS, output.code());
    Assert.assertEquals(expected, output.message());
  }

  @Test
  public void lsrTest() throws IOException, TachyonException {
    FileInfo[] files = new FileInfo[4];
    TachyonFile fileA = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileA",
            TachyonStorageType.STORE, UnderStorageType.NO_PERSIST, 10);
    files[0] = mTfs.getInfo(fileA);
    TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testDir/testFileB", TachyonStorageType.STORE,
            UnderStorageType.NO_PERSIST, 20);
    files[1] = mTfs.getInfo(mTfs.open(new TachyonURI("/testRoot/testDir")));
    files[2] = mTfs.getInfo(mTfs.open(new TachyonURI("/testRoot/testDir/testFileB")));
    TachyonFile fileC = TachyonFSTestUtils.createByteFile(mTfs, "/testRoot/testFileC",
            TachyonStorageType.NO_STORE, UnderStorageType.SYNC_PERSIST, 30);
    files[3] = mTfs.getInfo(fileC);

    InterpreterResult output = tachyonInterpreter.interpret("lsr /testRoot", null);

    String expected = "";
    String format = "%-10s%-25s%-15s%-5s\n";
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(10),
                    TfsShell.convertMsToDate(files[0].getCreationTimeMs()), "In Memory",
                    "/testRoot/testFileA");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(0),
                    TfsShell.convertMsToDate(files[1].getCreationTimeMs()), "", "/testRoot/testDir");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(20),
                    TfsShell.convertMsToDate(files[2].getCreationTimeMs()), "In Memory",
                    "/testRoot/testDir/testFileB");
    expected +=
            String.format(format, FormatUtils.getSizeFromBytes(30),
                    TfsShell.convertMsToDate(files[3].getCreationTimeMs()), "Not In Memory",
                    "/testRoot/testFileC");
    expected += "\n";
    Assert.assertEquals(expected, output.message());
  }

  @Test
  public void mkdirComplexPathTest() throws IOException, TachyonException {
    InterpreterResult output = tachyonInterpreter.interpret(
            "mkdir /Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File", null);

    TachyonFile tFile = mTfs.open(new TachyonURI("/Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File"));
    FileInfo fileInfo = mTfs.getInfo(tFile);
    Assert.assertNotNull(fileInfo);
    Assert.assertEquals(
            "Successfully created directory /Complex!@#$%^&*()-_=+[]{};\"'<>,.?/File\n\n",
            output.message());
    Assert.assertTrue(fileInfo.isIsFolder());
  }

  @Test
  public void mkdirExistingTest() throws IOException {
    String command = "mkdir /festFile1";
    Assert.assertEquals(Code.SUCCESS, tachyonInterpreter.interpret(command, null).code());
    Assert.assertEquals(Code.SUCCESS, tachyonInterpreter.interpret(command, null).code());
  }

  @Test
  public void mkdirInvalidPathTest() throws IOException {
    Assert.assertEquals(
            Code.ERROR,
            tachyonInterpreter.interpret("mkdir /test File Invalid Path", null).code());
  }

  @Test
  public void mkdirShortPathTest() throws IOException, TachyonException {
    InterpreterResult output = tachyonInterpreter.interpret("mkdir /root/testFile1", null);
    TachyonFile tFile = mTfs.open(new TachyonURI("/root/testFile1"));
    FileInfo fileInfo = mTfs.getInfo(tFile);
    Assert.assertNotNull(fileInfo);
    Assert.assertEquals(
            "Successfully created directory /root/testFile1\n\n",
            output.message());
    Assert.assertTrue(fileInfo.isIsFolder());
  }

  @Test
  public void mkdirTest() throws IOException, TachyonException {
    String qualifiedPath =
            "tachyon://" + mLocalTachyonCluster.getMasterHostname() + ":"
                    + mLocalTachyonCluster.getMasterPort() + "/root/testFile1";
    InterpreterResult output = tachyonInterpreter.interpret("mkdir " + qualifiedPath, null);
    TachyonFile tFile = mTfs.open(new TachyonURI("/root/testFile1"));
    FileInfo fileInfo = mTfs.getInfo(tFile);
    Assert.assertNotNull(fileInfo);
    Assert.assertEquals(
            "Successfully created directory " + qualifiedPath + "\n\n",
            output.message());
    Assert.assertTrue(fileInfo.isIsFolder());
  }

  private File generateFileContent(String path, byte[] toWrite)
          throws IOException, FileNotFoundException {
    File testFile = new File(mLocalTachyonCluster.getTachyonHome() + path);
    testFile.createNewFile();
    FileOutputStream fos = new FileOutputStream(testFile);
    fos.write(toWrite);
    fos.close();
    return testFile;
  }

  private byte[] readContent(TachyonFile tFile, int length) throws IOException, TachyonException {
    InStreamOptions options =
            new InStreamOptions.Builder(new TachyonConf()).setTachyonStorageType(
                    TachyonStorageType.NO_STORE).build();
    FileInStream tfis = mTfs.getInStream(tFile, options);
    byte[] read = new byte[length];
    tfis.read(read);
    return read;
  }

  private void fileReadTest(String fileName, int size) throws IOException {
    File testFile = new File(PathUtils.concatPath(mLocalTachyonCluster.getTachyonHome(), fileName));
    FileInputStream fis = new FileInputStream(testFile);
    byte[] read = new byte[size];
    fis.read(read);
    fis.close();
    Assert.assertTrue(BufferUtils.equalIncreasingByteArray(size, read));
  }
}