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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class to create / move / delete / write to / read from HDFS filessytem
 */
public class HdfsUtils {
  protected String dataPath;

  /**
   * @param dataPath Full hdfs path (including scheme) to notes root dir
   * @throws URISyntaxException
   */
  public HdfsUtils(String dataPath, String hadoopConfDir) throws URISyntaxException {
    if (hadoopConfDir == null) {
      throw new URISyntaxException(hadoopConfDir, "hadoopConfDir connaot be null");
    }
    if (hadoopConfDir.equals("")) {
      throw new URISyntaxException(hadoopConfDir, "hadoopConfDir connaot be empty");
    }
    final Path coreSite = new Path(hadoopConfDir, "core-site.xml");
    conf.addResource(coreSite);
    final Path hdfsSite = new Path(hadoopConfDir, "hdfs-site.xml");
    conf.addResource(hdfsSite);

    this.dataPath = dataPath;
  }


  protected Configuration conf = new Configuration();


  /**
   * @param directory Folder to list files
   * @return list of files available in this directory
   * @throws IOException
   */
  public Path[] listFiles(Path directory) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      FileStatus[] statuses = fs.listStatus(directory);
      List<Path> paths = new LinkedList<>();
      for (FileStatus status : statuses) {
        paths.add(status.getPath());
      }
      return paths.toArray(new Path[0]);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path Absolute apth without scheme
   * @throws IOException
   */
  public void delete(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      fs.delete(path, true);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path Absolute path without scheme
   * @throws IOException
   */
  public void mkdirs(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      fs.mkdirs(path);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param content data to write
   * @param path    absolute path without scheme
   * @throws IOException
   */
  public void writeFile(byte[] content, Path path) throws IOException {
    FileSystem fs = null;
    FSDataOutputStream fout = null;
    try {
      fs = FileSystem.get(conf);
      fout = fs.create(path, true);
      fout.write(content);
    } finally {
      if (fout != null)
        fout.close();
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param oldPath file to rename
   * @param newPath new file name
   * @throws IOException
   */
  public void rename(Path oldPath, Path newPath) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      fs.rename(oldPath, newPath);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public boolean exists(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      return fs.exists(path);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public boolean isDirectory(Path path) throws IOException {
    FileSystem fs = null;
    try {
      fs = FileSystem.get(conf);
      return fs.isDirectory(path);
    } finally {
      if (fs != null)
        fs.close();
    }
  }

  /**
   * @param path
   * @return
   * @throws IOException
   */
  public byte[] readFile(Path path) throws IOException {
    FileSystem fs = null;
    BufferedReader br = null;
    FSDataInputStream in = null;
    try {
      fs = FileSystem.get(conf);
      long fileLen = fs.getFileStatus(path).getLen();
      byte[] toRead = new byte[(int) fileLen];
      in = fs.open(path);
      in.readFully(0, toRead);
      return toRead;
    } finally {
      if (in != null)
        in.close();
      if (br != null)
        br.close();
      if (fs != null)
        fs.close();
    }
  }
}
