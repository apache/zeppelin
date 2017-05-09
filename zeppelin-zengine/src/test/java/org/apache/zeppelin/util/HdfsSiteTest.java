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
import org.apache.zeppelin.conf.ZeppelinConfiguration;
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
      HdfsSite hdfsSite = new HdfsSite(new ZeppelinConfiguration());
      hdfsSite.mkdirs("/tmp/coucou");
      String[] paths = hdfsSite.listFiles("/tmp");
      for (String path : paths) {
        System.out.println(path);
      }
      hdfsSite.delete("/tmp/coucou");
      paths = hdfsSite.listFiles("/tmp");
      for (String path : paths) {
        System.out.println(path);
      }
      hdfsSite.writeFile("This is mmy file content".getBytes("UTF-8"), "/tmp/coucou.txt");
      hdfsSite.rename("/tmp/coucou.txt", "/tmp/coucou2.txt");
      String read = new String(hdfsSite.readFile("/tmp/coucou2.txt"), "UTF-8");
      System.out.println(read);
      System.out.println(""+ hdfsSite.exists("/tmp/coucou2.txt"));
      hdfsSite.delete("/tmp/coucou2.txt");
    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
      assert(false);
    }
  }

}
