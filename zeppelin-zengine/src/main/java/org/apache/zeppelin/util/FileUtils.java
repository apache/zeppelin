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

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class FileUtils {

  public static void atomicWriteToFile(String content, File file, Set<PosixFilePermission> permissions) throws IOException {
    FileSystem defaultFileSystem = FileSystems.getDefault();
    Path destinationFilePath = defaultFileSystem.getPath(file.getCanonicalPath());
    Path destinationDirectory = destinationFilePath.getParent();
    Files.createDirectories(destinationDirectory);
    File tempFile = Files.createTempFile(destinationDirectory, file.getName(), null).toFile();
    if (permissions != null && !permissions.isEmpty()) {
      Files.setPosixFilePermissions(tempFile.toPath(), permissions);
    }
    try (FileOutputStream out = new FileOutputStream(tempFile)) {
      IOUtils.write(content, out);
    } catch (IOException iox) {
      if (!tempFile.delete()) {
        tempFile.deleteOnExit();
      }
      throw iox;
    }
    try {
      file.getParentFile().mkdirs();
      Files.move(tempFile.toPath(), destinationFilePath,
              StandardCopyOption.REPLACE_EXISTING); //StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException iox) {
      if (!tempFile.delete()) {
        tempFile.deleteOnExit();
      }
      throw iox;
    }
  }

  public static void atomicWriteToFile(String content, File file) throws IOException {
    atomicWriteToFile(content, file, null);
  }

  public static String readFromFile(File file) throws IOException {
    try (FileInputStream is = new FileInputStream(file)) {
      return IOUtils.toString(is);
    }
  }
}
