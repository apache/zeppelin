package org.apache.zeppelin.cluster.yarn;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

  static List<Path> getPathsFromDirPath(java.nio.file.Path dirPath) {
    if (null == dirPath || Files.notExists(dirPath) || !Files.isDirectory(dirPath)) {
      return Lists.newArrayList();
    }

    try {
      DirectoryStream<Path> directoryStream =
          Files.newDirectoryStream(dirPath, new DirectoryStream.Filter<java.nio.file.Path>() {
            @Override
            public boolean accept(java.nio.file.Path entry) throws IOException {
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
      Map<String, LocalResource> localResourceMap, List<java.nio.file.Path> paths) {
    for (java.nio.file.Path path : paths) {
      String resourcePath = appId + org.apache.hadoop.fs.Path.SEPARATOR + path.getFileName().toString();
      org.apache.hadoop.fs.Path dst = new org.apache.hadoop.fs.Path(fs.getHomeDirectory(), resourcePath);
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
  }
}
