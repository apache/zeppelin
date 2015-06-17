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
package org.apache.zeppelin.flink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.Iterator;
import scala.reflect.io.AbstractFile;
import scala.reflect.io.VirtualDirectory;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;

/**
 * Scala compiler
 */
public class FlinkIMain extends IMain {
  Logger logger = LoggerFactory.getLogger(FlinkIMain.class);

  public FlinkIMain(Settings setting, PrintWriter out) {
    super(setting, out);
  }

  public File jar() throws IOException {
    VirtualDirectory classDir = virtualDirectory();
    // create execution environment
    File jarBuildDir = new File(System.getProperty("java.io.tmpdir")
        + "/ZeppelinFlinkJarBiuldDir_" + System.currentTimeMillis());
    jarBuildDir.mkdirs();

    File jarFile = new File(System.getProperty("java.io.tmpdir")
        + "/ZeppelinFlinkJarFile_" + System.currentTimeMillis() + ".jar");


    Iterator<AbstractFile> vdIt = classDir.iterator();
    while (vdIt.hasNext()) {
      AbstractFile fi = vdIt.next();
      if (fi.isDirectory()) {
        Iterator<AbstractFile> fiIt = fi.iterator();
        while (fiIt.hasNext()) {
          AbstractFile f = fiIt.next();

          // directory for compiled line
          File lineDir = new File(jarBuildDir.getAbsolutePath(), fi.name());
          lineDir.mkdirs();

          // compiled classes for commands from shell
          File writeFile = new File(lineDir.getAbsolutePath(), f.name());
          FileOutputStream outputStream = new FileOutputStream(writeFile);
          InputStream inputStream = f.input();

          // copy file contents
          org.apache.commons.io.IOUtils.copy(inputStream, outputStream);

          inputStream.close();
          outputStream.close();
        }
      }
    }

    // jar up
    JarHelper jh = new JarHelper();
    jh.jarDir(jarBuildDir, jarFile);

    FileUtils.deleteDirectory(jarBuildDir);
    return jarFile;
  }


}
