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
package org.apache.zeppelin.notebook.repo.settings;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.GitNotebookRepo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinHubRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.NotebookRepoSettingUtils;
import org.junit.Test;

public class NotebookRepoSettingUtilsTest {

  @Test
  public void requireReloadTest() throws IOException {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    AuthenticationInfo subject = AuthenticationInfo.ANONYMOUS;
    GitNotebookRepo gitRepo = new GitNotebookRepo(conf);
    NotebookRepoWithSettings repoSettings = NotebookRepoWithSettings.
        builder(gitRepo.getClass().getSimpleName()).
        className(gitRepo.getClass().getName()).
        settings(gitRepo.getSettings(subject)).
        build();

    assertThat(NotebookRepoSettingUtils.requiresReload(repoSettings)).isEqualTo(false);
    
    ZeppelinHubRepo hubRepo = new ZeppelinHubRepo(conf);
    repoSettings = NotebookRepoWithSettings.
        builder(hubRepo.getClass().getSimpleName()).
        className(hubRepo.getClass().getName()).
        settings(hubRepo.getSettings(subject)).
        build();

    assertThat(NotebookRepoSettingUtils.requiresReload(repoSettings)).isEqualTo(false);
  }
}
