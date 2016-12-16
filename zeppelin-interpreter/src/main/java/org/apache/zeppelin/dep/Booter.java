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
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;

import java.nio.file.Paths;

/**
 * Manage mvn repository.
 */
public class Booter {
  public static RepositorySystem newRepositorySystem() {
    return RepositorySystemFactory.newRepositorySystem();
  }

  public static RepositorySystemSession newRepositorySystemSession(
      RepositorySystem system, String localRepoPath) {
    Validate.notNull(localRepoPath, "localRepoPath should have a value");

    MavenRepositorySystemSession session = new MavenRepositorySystemSession();

    LocalRepository localRepo = new LocalRepository(resolveLocalRepoPath(localRepoPath));
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepo));

    // session.setTransferListener(new ConsoleTransferListener());
    // session.setRepositoryListener(new ConsoleRepositoryListener());

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

  public static RemoteRepository newCentralRepository() {
    return new RemoteRepository("central", "default", "http://repo1.maven.org/maven2/");
  }

  public static RemoteRepository newLocalRepository() {
    return new RemoteRepository("local",
        "default", "file://" + System.getProperty("user.home") + "/.m2/repository");
  }
}
