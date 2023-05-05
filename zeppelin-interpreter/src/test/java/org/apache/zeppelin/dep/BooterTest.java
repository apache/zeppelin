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

package org.apache.zeppelin.dep;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class BooterTest {

  @Test
  void should_return_absolute_path() {
    String resolvedPath = Booter.resolveLocalRepoPath("path");
    assertTrue(Paths.get(resolvedPath).isAbsolute());
  }

  @Test
  void should_not_change_absolute_path() {
    String absolutePath
        = Paths.get("first", "second").toAbsolutePath().toString();
    String resolvedPath = Booter.resolveLocalRepoPath(absolutePath);
    assertEquals(absolutePath, resolvedPath);
  }

  @Test
  void should_throw_exception_for_null() {
    assertThrows(NullPointerException.class, () -> {
      Booter.resolveLocalRepoPath(null);
    });

  }

  @Test
  void getInterpreterMvnRepoPathTest() {
    ZeppelinConfiguration.reset();
    ZeppelinConfiguration.create("zeppelin-site-test.xml");
    List<RemoteRepository> remoteRepositories = Booter.newCentralRepositorys(null);
    assertNotNull(remoteRepositories);
    assertEquals(2, remoteRepositories.size());
    assertEquals("https://repo1.maven.org/maven2/", remoteRepositories.get(0).getUrl());
    assertEquals("https://repo2.maven.org/maven2/", remoteRepositories.get(1).getUrl());
  }
}
