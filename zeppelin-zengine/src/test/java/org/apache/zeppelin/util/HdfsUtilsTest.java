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

import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class HdfsUtilsTest {

  @BeforeClass
  public static void beforeStartUp() {
  }

  @Before
  public void startUp() {

  }

  @After
  public void shutDown() {

  }

  @Test
  public void rootPath() {
    try {
      HdfsUtils hdfsUtils = new HdfsUtils("/", null);
      hdfsUtils.mkdirs(new Path("/tmp/coucou"));
      Path[] paths = hdfsUtils.listFiles(new Path("/tmp"));
      for (Path path : paths) {
        System.out.println(path);
      }
      hdfsUtils.delete(new Path("/tmp/coucou"));
      paths = hdfsUtils.listFiles(new Path("/tmp"));
      for (Path path : paths) {
        System.out.println(path);
      }
      hdfsUtils.writeFile("This is mmy file contànt".getBytes("UTF-8"), new Path("/tmp/coucou.txt"));
      hdfsUtils.rename(new Path("/tmp/coucou.txt"), new Path("/tmp/coucou2.txt"));
      String read = new String(hdfsUtils.readFile(new Path("/tmp/coucou2.txt")), "UTF-8");
      System.out.println(read);
      System.out.println(""+hdfsUtils.exists(new Path("/tmp/coucou2.txt")));
      hdfsUtils.delete(new Path("/tmp/coucou2.txt"));



    } catch (URISyntaxException e) {
      e.printStackTrace();
      assert(false);
    } catch (IOException e) {
      e.printStackTrace();
      assert(false);
    }
  }

}
