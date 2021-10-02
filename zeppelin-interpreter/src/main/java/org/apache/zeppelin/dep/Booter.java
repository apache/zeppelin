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

import org.apache.commons.lang3.Validate;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manage mvn repository.
 */
public class Booter {
  private static final Logger LOGGER = LoggerFactory.getLogger(Booter.class);

  private Booter() {
    // only a helper
  }

  public static RepositorySystem newRepositorySystem() {
    return RepositorySystemFactory.newRepositorySystem();
  }

  public static RepositorySystemSession newRepositorySystemSession(
      RepositorySystem system, String localRepoPath) {
    Validate.notNull(localRepoPath, "localRepoPath should have a value");

    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    LocalRepository localRepo = new LocalRepository(resolveLocalRepoPath(localRepoPath));
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

    if (LOGGER.isDebugEnabled()) {
      session.setTransferListener(new TransferListener());
      session.setRepositoryListener(new RepositoryListener());
    }
    // uncomment to generate dirty trees
    // session.setDependencyGraphTransformer( null );

    return session;
  }

  static String resolveLocalRepoPath(String localRepoPath) {
    // todo decouple home folder resolution
    // find homedir
    String home = System.getenv("ZEPPELIN_HOME");
    if (home == null) {
      home = System.getProperty("zeppelin.home");
    }
    if (home == null) {
      home = "..";
    }

    return Paths.get(home).resolve(localRepoPath).toAbsolutePath().toString();
  }

  public static List<RemoteRepository> newCentralRepositorys(Proxy proxy) {
    String mvnRepoEnv = System.getenv("ZEPPELIN_INTERPRETER_DEP_MVNREPO");
    if (mvnRepoEnv == null) {
      mvnRepoEnv = ZeppelinConfiguration.create().getString(
              ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_DEP_MVNREPO);
    }
    if (mvnRepoEnv == null) {
      mvnRepoEnv = "https://repo1.maven.org/maven2/";
    }

    List<String> repoList = new ArrayList<>();
    if (mvnRepoEnv.contains(",")) {
      repoList.addAll(Arrays.asList(mvnRepoEnv.split(",+")));
    } else {
      repoList.add(mvnRepoEnv);
    }

    List<RemoteRepository> centralRepositorys = repoList.stream().map(repo -> {
              RemoteRepository.Builder centralBuilder = new RemoteRepository.Builder("central", "default", repo);
              if (proxy != null) {
                centralBuilder.setProxy(proxy);
              }
              return centralBuilder.build();
            }
    ).collect(Collectors.toList());
    return centralRepositorys;
  }

  public static RemoteRepository newLocalRepository() {
    return new RemoteRepository.Builder("local",
        "default", "file://" + System.getProperty("user.home") + "/.m2/repository").build();
  }
}
