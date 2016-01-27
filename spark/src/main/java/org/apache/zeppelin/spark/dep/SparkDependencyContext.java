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

package org.apache.zeppelin.spark.dep;

import java.io.File;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.zeppelin.dep.Booter;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.Repository;

import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;

import scala.Console;


/**
 *
 */
public class SparkDependencyContext {
  List<Dependency> dependencies = new LinkedList<Dependency>();
  List<Repository> repositories = new LinkedList<Repository>();

  List<File> files = new LinkedList<File>();
  List<File> filesDist = new LinkedList<File>();
  private RepositorySystem system = Booter.newRepositorySystem();
  private RepositorySystemSession session;
  private RemoteRepository mavenCentral = Booter.newCentralRepository();
  private RemoteRepository mavenLocal = Booter.newLocalRepository();
  private List<RemoteRepository> additionalRepos = new LinkedList<RemoteRepository>();

  public SparkDependencyContext(String localRepoPath, String additionalRemoteRepository) {
    session =  Booter.newRepositorySystemSession(system, localRepoPath);
    addRepoFromProperty(additionalRemoteRepository);
  }

  public Dependency load(String lib) {
    Console.println("DepInterpreter(%dep) deprecated. "
        + "Load dependency through GUI interpreter menu instead.");
    Dependency dep = new Dependency(lib);

    if (dependencies.contains(dep)) {
      dependencies.remove(dep);
    }
    dependencies.add(dep);
    return dep;
  }

  public Repository addRepo(String name) {
    Console.println("DepInterpreter(%dep) deprecated. "
        + "Add repository through GUI interpreter menu instead.");
    Repository rep = new Repository(name);
    repositories.add(rep);
    return rep;
  }

  public void reset() {
    Console.println("DepInterpreter(%dep) deprecated. "
        + "Remove dependencies and repositories through GUI interpreter menu instead.");
    dependencies = new LinkedList<Dependency>();
    repositories = new LinkedList<Repository>();

    files = new LinkedList<File>();
    filesDist = new LinkedList<File>();
  }

  private void addRepoFromProperty(String listOfRepo) {
    if (listOfRepo != null) {
      String[] repos = listOfRepo.split(";");
      for (String repo : repos) {
        String[] parts = repo.split(",");
        if (parts.length == 3) {
          String id = parts[0].trim();
          String url = parts[1].trim();
          boolean isSnapshot = Boolean.parseBoolean(parts[2].trim());
          if (id.length() > 1 && url.length() > 1) {
            RemoteRepository rr = new RemoteRepository(id, "default", url);
            rr.setPolicy(isSnapshot, null);
            additionalRepos.add(rr);
          }
        }
      }
    }
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
    Artifact artifact = new DefaultArtifact(
        SparkDependencyResolver.inferScalaVersion(dep.getGroupArtifactVersion()));

    DependencyFilter classpathFlter = DependencyFilterUtils
        .classpathFilter(JavaScopes.COMPILE);
    PatternExclusionsDependencyFilter exclusionFilter = new PatternExclusionsDependencyFilter(
        SparkDependencyResolver.inferScalaVersion(dep.getExclusions()));

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new org.sonatype.aether.graph.Dependency(artifact,
        JavaScopes.COMPILE));

    collectRequest.addRepository(mavenCentral);
    collectRequest.addRepository(mavenLocal);
    for (RemoteRepository repo : additionalRepos) {
      collectRequest.addRepository(repo);
    }
    for (Repository repo : repositories) {
      RemoteRepository rr = new RemoteRepository(repo.getId(), "default", repo.getUrl());
      rr.setPolicy(repo.isSnapshot(), null);
      Authentication auth = repo.getAuthentication();
      if (auth != null) {
        rr.setAuthentication(auth);
      }
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
