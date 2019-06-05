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

package org.apache.zeppelin.shell.terminal.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IOHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(IOHelper.class);

  public static void close(Closeable... closables) {
    for (Closeable closable : closables) {
      try {
        closable.close();
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  public static synchronized void copyLibPty(Path dataDir) throws IOException {
    Path donePath = dataDir.resolve(".DONE");

    if (Files.exists(donePath)) {
      return;
    }

    String jarPath = getClassPath(IOHelper.class) + File.separator;
    Set<String> nativeFiles = getNativeFiles();
    for (String nativeFile : nativeFiles) {
      Path nativePath = dataDir.resolve(nativeFile);

      if (Files.notExists(nativePath)) {
        Files.createDirectories(nativePath.getParent());
        InputStream inputStream = IOHelper.class.getResourceAsStream("/" + nativeFile);
        if (null == inputStream) {
          Path source = Paths.get(jarPath + nativeFile);
          if (!Files.exists(source)) {
            throw new IOException("Can't find pytlib file : " + jarPath + nativeFile);
          } else {
            LOGGER.info("Use the pytlib file {} outside the JAR package.", jarPath + nativeFile);
          }
          Files.copy(source, nativePath);
        } else {
          LOGGER.info("Use the libpty file {} in the JAR package resource.", nativeFile);
          Files.copy(inputStream, nativePath);
          close(inputStream);
        }
      }
    }

    Files.createFile(donePath);
  }

  private static Set<String> getNativeFiles() {
    final Set<String> nativeFiles = new HashSet<>();

    List<String> freebsd = Arrays.asList(
        "libpty/freebsd/x86/libpty.so", "libpty/freebsd/x86_64/libpty.so");
    List<String> linux = Arrays.asList(
        "libpty/linux/x86/libpty.so", "libpty/linux/x86_64/libpty.so");
    List<String> macosx = Arrays.asList(
        "libpty/macosx/x86/libpty.dylib", "libpty/macosx/x86_64/libpty.dylib");
    List<String> win_x86 = Arrays.asList(
        "libpty/win/x86/winpty.dll", "libpty/win/x86/winpty-agent.exe");
    List<String> win_x86_64 = Arrays.asList(
        "libpty/win/x86_64/winpty.dll", "libpty/win/x86_64/winpty-agent.exe",
        "libpty/win/x86_64/cyglaunch.exe");
    List<String> win_xp = Arrays.asList(
        "libpty/win/xp/winpty.dll", "libpty/win/xp/winpty-agent.exe");

    nativeFiles.addAll(freebsd);
    nativeFiles.addAll(linux);
    nativeFiles.addAll(macosx);
    nativeFiles.addAll(win_x86);
    nativeFiles.addAll(win_x86_64);
    nativeFiles.addAll(win_xp);

    return nativeFiles;
  }

  private static String getClassPath(Class clazz) throws UnsupportedEncodingException {
    // Check if the parameters passed in by the user are empty
    if (clazz == null) {
      throw new java.lang.IllegalArgumentException("The parameter cannot be empty!");
    }

    ClassLoader loader = clazz.getClassLoader();
    // Get the full name of the class, including the package name
    String clsName = clazz.getName() + ".class";
    // Get the package where the incoming parameters are located
    Package pack = clazz.getPackage();
    String path = "";
    // If not an anonymous package, convert the package name to a path
    if (pack != null) {
      String packName = pack.getName();
      // Here is a simple decision to determine whether it is a Java base class library,
      // preventing users from passing in the JDK built-in class library.
      if (packName.startsWith("java.") || packName.startsWith("javax.")) {
        throw new java.lang.IllegalArgumentException("Do not transfer system classes!");
      }

      // In the name of the class, remove the part of the package name
      // and get the file name of the class.
      clsName = clsName.substring(packName.length() + 1);
      // Determine whether the package name is a simple package name, and if so,
      // directly convert the package name to a path.
      if (packName.indexOf(".") < 0) {
        path = packName + "/";
      } else {
        // Otherwise, the package name is converted to a path according
        // to the component part of the package name.
        int start = 0, end = 0;
        end = packName.indexOf(".");
        while (end != -1) {
          path = path + packName.substring(start, end) + "/";
          start = end + 1;
          end = packName.indexOf(".", start);
        }
        path = path + packName.substring(start) + "/";
      }
    }
    // Call the classReloader's getResource method, passing in the
    // class file name containing the path information.
    java.net.URL url = loader.getResource(path + clsName);
    // Get path information from the URL object
    String realPath = url.getPath();
    // Remove the protocol name "file:" in the path information.
    int pos = realPath.indexOf("file:");
    if (pos > -1) {
      realPath = realPath.substring(pos + 5);
    }
    // Remove the path information and the part that contains the class file information,
    // and get the path where the class is located.
    pos = realPath.indexOf(path + clsName);
    realPath = realPath.substring(0, pos - 1);
    // If the class file is packaged into a JAR file, etc.,
    // remove the corresponding JAR and other package file names.
    if (realPath.endsWith("!")) {
      realPath = realPath.substring(0, realPath.lastIndexOf("/"));
    }

    try {
      realPath = java.net.URLDecoder.decode(realPath, "utf-8");
    } catch (UnsupportedEncodingException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return realPath;
  }

}
