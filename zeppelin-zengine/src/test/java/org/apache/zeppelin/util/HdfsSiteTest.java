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

import java.io.IOException;
import java.net.URISyntaxException;

public class HdfsSiteTest {

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
      HdfsSite hdfsSite = new HdfsSite(null);
      hdfsSite.mkdirs(new Path("/tmp/coucou"));
      Path[] paths = hdfsSite.listFiles(new Path("/tmp"));
      for (Path path : paths) {
        System.out.println(path);
      }
      hdfsSite.delete(new Path("/tmp/coucou"));
      paths = hdfsSite.listFiles(new Path("/tmp"));
      for (Path path : paths) {
        System.out.println(path);
      }
      hdfsSite.writeFile("This is mmy file content".getBytes("UTF-8"), new Path("/tmp/coucou.txt"));
      hdfsSite.rename(new Path("/tmp/coucou.txt"), new Path("/tmp/coucou2.txt"));
      String read = new String(hdfsSite.readFile(new Path("/tmp/coucou2.txt")), "UTF-8");
      System.out.println(read);
      System.out.println(""+ hdfsSite.exists(new Path("/tmp/coucou2.txt")));
      hdfsSite.delete(new Path("/tmp/coucou2.txt"));



    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
      assert(false);
    }
  }

}
