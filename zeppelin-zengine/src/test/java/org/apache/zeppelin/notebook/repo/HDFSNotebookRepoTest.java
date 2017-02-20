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

package org.apache.zeppelin.notebook.repo;


import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HDFSNotebookRepoTest {
  @Test
  public void removeProtocolTest() {
    String hdfsUrlNoProtocol1 = "/user/foo/notebook";
    String hdfsUrlNoProtocol2 = "/user/foo/notebook/";
    String hdfsUrlWithProtocol1 = "hdfs://namenode/user/foo/notebook";
    String hdfsUrlWithProtocol2 = "hdfs://dummyhost:8020/user/foo/notebook";

    ZeppelinConfiguration conf = new ZeppelinConfiguration();
    HDFSNotebookRepo repo = null;
    try {
      repo = new HDFSNotebookRepo(conf);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertEquals("hdfsUrlNoProtocol1", "/user/foo/notebook", repo.removeProtocol(hdfsUrlNoProtocol1));
    assertEquals("hdfsUrlNoProtocol2", "/user/foo/notebook", repo.removeProtocol(hdfsUrlNoProtocol2));
    assertEquals("hdfsUrlWithProtocol1", "/user/foo/notebook", repo.removeProtocol(hdfsUrlWithProtocol1));
    assertEquals("hdfsUrlWithProtocol2", "/user/foo/notebook", repo.removeProtocol(hdfsUrlWithProtocol2));
  }
}
