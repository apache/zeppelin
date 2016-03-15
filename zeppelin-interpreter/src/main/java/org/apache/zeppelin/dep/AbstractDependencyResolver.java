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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactResult;

/**
 * Abstract dependency resolver.
 * Add new dependencies from mvn repo (at runtime) Zeppelin.
 */
public abstract class AbstractDependencyResolver {
  protected RepositorySystem system = Booter.newRepositorySystem();
  protected List<RemoteRepository> repos = new LinkedList<RemoteRepository>();
  protected RepositorySystemSession session;
  
  public AbstractDependencyResolver(String localRepoPath) {
    session = Booter.newRepositorySystemSession(system, localRepoPath);
    repos.add(Booter.newCentralRepository()); // add maven central
    repos.add(Booter.newLocalRepository());
  }

  public List<RemoteRepository> getRepos() {
    return this.repos;
  }
  
  public void addRepo(String id, String url, boolean snapshot) {
    synchronized (repos) {
      delRepo(id);
      RemoteRepository rr = new RemoteRepository(id, "default", url);
      rr.setPolicy(true, new RepositoryPolicy(
          snapshot,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_WARN));
      repos.add(rr);
    }
  }

  public void addRepo(String id, String url, boolean snapshot, Authentication auth) {
    synchronized (repos) {
      delRepo(id);
      RemoteRepository rr = new RemoteRepository(id, "default", url);
      rr.setPolicy(true, new RepositoryPolicy(
          snapshot,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_WARN));
      rr.setAuthentication(auth);
      repos.add(rr);
    }
  }

  public RemoteRepository delRepo(String id) {
    synchronized (repos) {
      Iterator<RemoteRepository> it = repos.iterator();
      while (it.hasNext()) {
        RemoteRepository repo = it.next();
        if (repo.getId().equals(id)) {
          it.remove();
          return repo;
        }
      }
    }
    return null;
  }

  public abstract List<ArtifactResult> getArtifactsWithDep(String dependency,
      Collection<String> excludes) throws Exception;
}
