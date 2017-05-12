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

package org.apache.zeppelin.cluster.yarn;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class YarnUtils {

  public static final Logger logger = LoggerFactory.getLogger(YarnUtils.class);

  static List<Path> getPathsFromDirPath(Path dirPath) {
    if (null == dirPath || Files.notExists(dirPath)) {
      return Lists.newArrayList();
    }

    if (Files.isRegularFile(dirPath)) {
      return Collections.singletonList(dirPath);
    }

    try {
      DirectoryStream<Path> directoryStream =
          Files.newDirectoryStream(dirPath, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              String filename = entry.toString();
              return filename.endsWith(".jar") || filename.endsWith(".zip");
            }
          });
      return Lists.newArrayList(directoryStream);
    } catch (IOException e) {
      logger.error("Cannot read directory: {}", dirPath.toString(), e);
      return Lists.newArrayList();
    }
  }

  static void addLocalResource(FileSystem fs, String appId,
      Map<String, LocalResource> localResourceMap, List<Path> paths) {
    String resourceDirPath = appId + org.apache.hadoop.fs.Path.SEPARATOR;
    for (Path path : paths) {
      String resourcePath =
          resourceDirPath + path.getFileName().toString();
      org.apache.hadoop.fs.Path dst = new org.apache.hadoop.fs.Path(fs.getHomeDirectory(),
          resourcePath);
      try {
        if (Files.exists(path) && !fs.exists(dst)) {
          fs.copyFromLocalFile(new org.apache.hadoop.fs.Path(path.toUri()), dst);
          FileStatus fileStatus = fs.getFileStatus(dst);
          LocalResourceType localResourceType = LocalResourceType.FILE;
          String filename = path.getFileName().toString();
          if (filename.endsWith(".zip")) {
            localResourceType = LocalResourceType.ARCHIVE;
          }
          LocalResource resource = LocalResource
              .newInstance(ConverterUtils.getYarnUrlFromPath(dst), localResourceType,
                  LocalResourceVisibility.APPLICATION, fileStatus.getLen(),
                  fileStatus.getModificationTime());
          localResourceMap.put(filename, resource);
        }
      } catch (IOException e) {
        logger.error("Error while copying resources into hdfs", e);
      }
    }
    try {
      fs.deleteOnExit(new org.apache.hadoop.fs.Path(resourceDirPath));
    } catch (IOException e) {
      logger.error("Error while removing {}", resourceDirPath, e);
    }
  }
}
