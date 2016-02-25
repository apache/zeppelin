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

import java.io.File;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;

import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;


/**
 *
 */
public class DependencyContext {
  List<Dependency> dependencies = new LinkedList<Dependency>();
  List<Repository> repositories = new LinkedList<Repository>();

  List<File> files = new LinkedList<File>();
  List<File> filesDist = new LinkedList<File>();
  private RepositorySystem system = Booter.newRepositorySystem();
  private RepositorySystemSession session;
  private RemoteRepository mavenCentral = Booter.newCentralRepository();
  private RemoteRepository mavenLocal = Booter.newLocalRepository();

  public DependencyContext(String localRepoPath) {
    session = Booter.newRepositorySystemSession(system, localRepoPath);
  }

  public Dependency load(String lib) {
    Dependency dep = new Dependency(lib);

    if (dependencies.contains(dep)) {
      dependencies.remove(dep);
    }
    dependencies.add(dep);
    return dep;
  }

  public Repository addRepo(String name) {
    Repository rep = new Repository(name);
    repositories.add(rep);
    return rep;
  }

  public void reset() {
    dependencies = new LinkedList<Dependency>();
    repositories = new LinkedList<Repository>();

    files = new LinkedList<File>();
    filesDist = new LinkedList<File>();
  }


  /**
   * fetch all artifacts
   * @return
   * @throws MalformedURLException
   * @throws ArtifactResolutionException
   * @throws DependencyResolutionException
   */
  public List<File> fetch() throws MalformedURLException,
      DependencyResolutionException, ArtifactResolutionException {

    for (Dependency dep : dependencies) {
      if (!dep.isLocalFsArtifact()) {
        List<ArtifactResult> artifacts = fetchArtifactWithDep(dep);
        for (ArtifactResult artifact : artifacts) {
          if (dep.isDist()) {
            filesDist.add(artifact.getArtifact().getFile());
          }
          files.add(artifact.getArtifact().getFile());
        }
      } else {
        if (dep.isDist()) {
          filesDist.add(new File(dep.getGroupArtifactVersion()));
        }
        files.add(new File(dep.getGroupArtifactVersion()));
      }
    }

    return files;
  }

  private List<ArtifactResult> fetchArtifactWithDep(Dependency dep)
      throws DependencyResolutionException, ArtifactResolutionException {
    Artifact artifact = new DefaultArtifact(dep.getGroupArtifactVersion());

    DependencyFilter classpathFlter = DependencyFilterUtils
        .classpathFilter(JavaScopes.COMPILE);
    PatternExclusionsDependencyFilter exclusionFilter = new PatternExclusionsDependencyFilter(
        dep.getExclusions());

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new org.sonatype.aether.graph.Dependency(artifact,
        JavaScopes.COMPILE));

    collectRequest.addRepository(mavenCentral);
    collectRequest.addRepository(mavenLocal);
    for (Repository repo : repositories) {
      RemoteRepository rr = new RemoteRepository(repo.getId(), "default", repo.getUrl());
      rr.setPolicy(repo.isSnapshot(), null);
      collectRequest.addRepository(rr);
    }

    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
        DependencyFilterUtils.andFilter(exclusionFilter, classpathFlter));

    return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
  }

  public List<File> getFiles() {
    return files;
  }

  public List<File> getFilesDist() {
    return filesDist;
  }
}
