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
package org.apache.zeppelin.interpreter;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InterpreterOutputChangeWatcherTest implements InterpreterOutputChangeListener {
  private File tmpDir;
  private File fileChanged;
  private AtomicInteger numChanged;
  private InterpreterOutputChangeWatcher watcher;

  @Before
  public void setUp() throws Exception {
    watcher = new InterpreterOutputChangeWatcher(this);
    watcher.start();

    tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
    tmpDir.mkdirs();
    fileChanged = null;
    numChanged = new AtomicInteger(0);
  }

  @After
  public void tearDown() throws Exception {
    watcher.shutdown();
    delete(tmpDir);
  }

  private void delete(File file){
    if(file.isFile()) file.delete();
    else if(file.isDirectory()){
      File [] files = file.listFiles();
      if(files!=null && files.length>0){
        for(File f : files){
          delete(f);
        }
      }
      file.delete();
    }
  }


  @Test
  public void test() throws IOException, InterruptedException {
    assertNull(fileChanged);
    assertEquals(0, numChanged.get());

    Thread.sleep(1000);
    // create new file
    File file1 = new File(tmpDir, "test1");
    file1.createNewFile();

    File file2 = new File(tmpDir, "test2");
    file2.createNewFile();

    watcher.watch(file1);
    Thread.sleep(1000);

    FileOutputStream out1 = new FileOutputStream(file1);
    out1.write(1);
    out1.close();

    FileOutputStream out2 = new FileOutputStream(file2);
    out2.write(1);
    out2.close();

    synchronized (this) {
      wait(30*1000);
    }

    assertNotNull(fileChanged);
    assertEquals(1, numChanged.get());
  }


  @Override
  public void fileChanged(File file) {
    fileChanged = file;
    numChanged.incrementAndGet();

    synchronized(this) {
      notify();
    }
  }

}