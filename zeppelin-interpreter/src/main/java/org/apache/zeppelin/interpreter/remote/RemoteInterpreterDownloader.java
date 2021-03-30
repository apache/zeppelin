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

package org.apache.zeppelin.interpreter.remote;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.interpreter.thrift.LibraryMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteInterpreterDownloader {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterDownloader.class);

  private static final int MAX_LIBRARY_DOWNLOAD_ATTEMPTS = 3;

  private final RemoteInterpreterEventClient client;
  private final String interpreter;
  private final File localRepoDir;

  public RemoteInterpreterDownloader(
      String interpreter,
      RemoteInterpreterEventClient client,
      File localRepoDir) {
    this.client = client;
    this.interpreter = interpreter;
    this.localRepoDir = localRepoDir;
  }

  public static void main(String[] args) {
    if (args.length == 4) {
      String zeppelinServerHost = args[0];
      int port = Integer.parseInt(args[1]);
      String interpreter = args[2];
      String localRepoPath = args[3];
      RemoteInterpreterEventClient intpEventClient = new RemoteInterpreterEventClient(
          zeppelinServerHost, port, 3);

      RemoteInterpreterDownloader downloader = new RemoteInterpreterDownloader(interpreter,
          intpEventClient, new File(localRepoPath));
      downloader.syncAllLibraries();
    } else {
      LOGGER.error(
          "Wrong amount of Arguments. Expected: [ZeppelinHostname] [ZeppelinPort] [InterpreterName] [LocalRepoPath]");
      // based on sysexits.h, 64 indicated a command line usage error
      System.exit(64);
    }
  }

  private void syncAllLibraries() {
    LOGGER.info("Loading all libraries for interpreter {} to {}", interpreter, localRepoDir);
    List<LibraryMetadata> metadatas = client.getAllLibraryMetadatas(interpreter);
    if (!localRepoDir.isDirectory()) {
      LOGGER.error("{} is no directory", localRepoDir);
      return;
    }
    Set<String> syncedLibraries = new HashSet<>();
    // Add or update new libraries
    for (LibraryMetadata metadata : metadatas) {
      File library = new File(localRepoDir, metadata.getName());
      addOrUpdateLibrary(library, metadata);
      syncedLibraries.add(metadata.getName());
    }
    // Delete old Jar files
    for (File file : FileUtils.listFiles(localRepoDir, new String[] { "jar" }, false)) {
      if (!syncedLibraries.contains(file.getName())) {
        try {
          LOGGER.info("Delete {}, because it's not present on the server side", file.toPath());
          Files.delete(file.toPath());
        } catch (IOException e) {
          LOGGER.error("Unable to delete old library {} during sync.", file, e);
        }
      }
    }
  }

  private void addOrUpdateLibrary(File library, LibraryMetadata metadata) {
    try {
      if (library.exists() && library.canRead()) {
        long localChecksum = FileUtils.checksumCRC32(library);
        if (localChecksum == metadata.getChecksum()) {
          // nothing to do if checksum is matching
          LOGGER.info("Library {} is present ", library.getName());
        } else {
          // checksum didn't match
          Files.delete(library.toPath());
          downloadLibrary(library, metadata);
        }
      } else {
        downloadLibrary(library, metadata);
      }
    } catch (IOException e) {
      LOGGER.error("Can not add or update library {}", library, e);
    }
  }

  private void downloadLibrary(File library, LibraryMetadata metadata) {
    LOGGER.debug("Trying to download library {} to {}", metadata.getName(), library);
    try {
      if (library.createNewFile()) {
        int attempt = 0;
        while (attempt < MAX_LIBRARY_DOWNLOAD_ATTEMPTS) {
          ByteBuffer bytes = client.getLibrary(interpreter, metadata.getName());
          if (bytes == null) {
            LOGGER.error("ByteBuffer of library {} is null."
                + " For a detailed message take a look into Zeppelin-Server log. Attempt {} of {}",
                metadata.getName(), ++attempt, MAX_LIBRARY_DOWNLOAD_ATTEMPTS);
          } else {
            FileUtils.writeByteArrayToFile(library, bytes.array());
            if (FileUtils.checksumCRC32(library) == metadata.getChecksum()) {
              LOGGER.info("Library {} successfully transfered", library.getName());
              break;
            } else {
              LOGGER.error("Library Checksum didn't match. Attempt {} of {}", ++attempt,
                  MAX_LIBRARY_DOWNLOAD_ATTEMPTS);
            }
          }
        }
      } else {
        LOGGER.error("Unable to create a new library file {}", library);
      }
    } catch (IOException e) {
      LOGGER.error("Unable to download Library {}", metadata.getName(), e);
    }
  }
}
