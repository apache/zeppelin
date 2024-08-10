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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract dependency resolver.
 * Add new dependencies from mvn repo (at runtime) Zeppelin.
 */
public abstract class AbstractDependencyResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDependencyResolver.class);

  protected RepositorySystem system = Booter.newRepositorySystem();
  protected List<RemoteRepository> repos = new LinkedList<>();
  protected RepositorySystemSession session;
  private Proxy proxy = null;

  protected AbstractDependencyResolver(String localRepoPath, ZeppelinConfiguration zConf) {
    if (zConf.getZeppelinProxyUrl() != null) {
      try {
        URL proxyUrl = new URL(zConf.getZeppelinProxyUrl());
        Authentication auth = new AuthenticationBuilder().addUsername(zConf.getZeppelinProxyUser()).addPassword(zConf.getZeppelinProxyPassword()).build();
        proxy = new Proxy(proxyUrl.getProtocol(), proxyUrl.getHost(), proxyUrl.getPort(), auth);
      } catch (MalformedURLException e) {
        LOGGER.error("Proxy Url {} is not valid - skipping Proxy config", zConf.getZeppelinProxyUrl(), e);
      }
    }
    session = Booter.newRepositorySystemSession(system, localRepoPath);
    repos.addAll(Booter.newCentralRepositorys(proxy, zConf)); // add maven central
    repos.add(Booter.newLocalRepository());
  }

  protected AbstractDependencyResolver(String localRepoPath, Proxy proxy,
      ZeppelinConfiguration zConf) {
    this.proxy = proxy;
    session = Booter.newRepositorySystemSession(system, localRepoPath);
    repos.addAll(Booter.newCentralRepositorys(proxy, zConf)); // add maven central
    repos.add(Booter.newLocalRepository());
  }

  public List<RemoteRepository> getRepos() {
    return this.repos;
  }

  public void addRepo(String id, String url, boolean snapshot) {
    synchronized (repos) {
      delRepo(id);
      RepositoryPolicy policy = new RepositoryPolicy(
          true,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_WARN);

      RemoteRepository.Builder rr = new RemoteRepository.Builder(id, "default", url);
      if (snapshot) {
        rr.setSnapshotPolicy(policy);
      } else {
        rr.setPolicy(policy);
      }

      if (proxy != null) {
        rr.setProxy(proxy);
      }
      repos.add(rr.build());
    }
  }

  public void addRepo(String id, String url, boolean snapshot, Authentication auth, Proxy proxy) {
    synchronized (repos) {
      delRepo(id);
      RepositoryPolicy policy = new RepositoryPolicy(
          true,
          RepositoryPolicy.UPDATE_POLICY_DAILY,
          RepositoryPolicy.CHECKSUM_POLICY_WARN);

      RemoteRepository.Builder rr = new RemoteRepository.Builder(id, "default", url).setAuthentication(auth).setProxy(proxy);
      if (snapshot) {
        rr.setSnapshotPolicy(policy);
      } else {
        rr.setPolicy(policy);
      }
      repos.add(rr.build());
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
