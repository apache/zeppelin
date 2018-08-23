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
package org.apache.zeppelin.notebook.repo.mock;

import java.io.IOException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;

public class VFSNotebookRepoMock extends VFSNotebookRepo {

  public VFSNotebookRepoMock() {
    String secNotebookDir = ZeppelinConfiguration.create().getNotebookDir() + "_secondary";
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), secNotebookDir);
  }

  public void init(ZeppelinConfiguration conf) throws IOException {
    super.init(conf);
  }
}
