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
package org.apache.zeppelin.interpreter.launcher.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class TarUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(TarUtils.class);

  public static void compress(String name, List<TarFileEntry> files) throws IOException {
    try (TarArchiveOutputStream out = getTarArchiveOutputStream(name)){
      for (TarFileEntry tarFileEntry : files){
        addToArchiveCompression(out, tarFileEntry.getFile(), tarFileEntry.getArchivePath());
      }
    }
  }

  public static void decompress(String in, File out) throws IOException {
    FileInputStream fileInputStream = new FileInputStream(in);
    GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(fileInputStream);

    try (TarArchiveInputStream fin = new TarArchiveInputStream(gzipInputStream)){
      TarArchiveEntry entry;
      while ((entry = fin.getNextTarEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        File curfile = new File(out, entry.getName());
        File parent = curfile.getParentFile();
        if (!parent.exists()) {
          parent.mkdirs();
        }
        IOUtils.copy(fin, new FileOutputStream(curfile));
      }
    }
  }

  private static TarArchiveOutputStream getTarArchiveOutputStream(String name)
      throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(name);
    GzipCompressorOutputStream gzipOutputStream = new GzipCompressorOutputStream(fileOutputStream);
    TarArchiveOutputStream taos = new TarArchiveOutputStream(gzipOutputStream);

    // TAR has an 8 gig file limit by default, this gets around that
    taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);

    // TAR originally didn't support long file names, so enable the support for it
    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    taos.setAddPaxHeadersForNonAsciiNames(true);

    return taos;
  }

  private static void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir)
      throws IOException {
    if (file.isFile()){
      String archivePath = "." + dir;
      LOGGER.info("archivePath = " + archivePath);
      out.putArchiveEntry(new TarArchiveEntry(file, archivePath));
      try (FileInputStream in = new FileInputStream(file)) {
        IOUtils.copy(in, out);
      }
      out.closeArchiveEntry();
    } else if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null){
        for (File child : children){
          String appendDir = child.getAbsolutePath().replace(file.getAbsolutePath(), "");
          addToArchiveCompression(out, child, dir + appendDir);
        }
      }
    } else {
      LOGGER.error(file.getName() + " is not supported");
    }
  }
}
