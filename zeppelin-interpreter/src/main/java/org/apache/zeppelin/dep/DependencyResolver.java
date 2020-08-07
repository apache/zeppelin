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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deps resolver.
 * Add new dependencies from mvn repo (at runtime) to Zeppelin.
 */
public class DependencyResolver extends AbstractDependencyResolver {
  private static final Logger LOGGER = LoggerFactory.getLogger(DependencyResolver.class);

  private final String[] exclusions = new String[] {"org.apache.zeppelin:zeppelin-zengine",
                                                    "org.apache.zeppelin:zeppelin-interpreter",
                                                    "org.apache.zeppelin:zeppelin-server"};

  public DependencyResolver(String localRepoPath) {
    super(localRepoPath);
  }

  public DependencyResolver(String localRepoPath, Proxy proxy) {
    super(localRepoPath, proxy);
  }

  public List<File> load(String artifact)
      throws RepositoryException {
    return load(artifact, new LinkedList<>());
  }

  public synchronized List<File> load(String artifact, Collection<String> excludes)
      throws RepositoryException {
    if (StringUtils.isBlank(artifact)) {
      // Skip dependency loading if artifact is empty
      return new LinkedList<>();
    }

    // <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
    int numSplits = artifact.split(":").length;
    if (numSplits >= 3 && numSplits <= 6) {
      return loadFromMvn(artifact, excludes);
    } else {
      LinkedList<File> libs = new LinkedList<>();
      libs.add(new File(artifact));
      return libs;
    }
  }

  public List<File> load(String artifact, File destPath) throws IOException, RepositoryException {
    return load(artifact, new LinkedList<>(), destPath);
  }

  public List<File> load(String artifact, Collection<String> excludes, File destPath)
      throws RepositoryException, IOException {
    List<File> libs = new LinkedList<>();

    if (StringUtils.isNotBlank(artifact)) {
      libs = load(artifact, excludes);

      for (File srcFile : libs) {
        File destFile = new File(destPath, srcFile.getName());
        if (!destFile.exists() || !FileUtils.contentEquals(srcFile, destFile)) {
          FileUtils.copyFile(srcFile, destFile);
          LOGGER.debug("copy {} to {}", srcFile.getAbsolutePath(), destPath);
        }
      }
    }
    return libs;
  }

  public synchronized void copyLocalDependency(String srcPath, File destPath)
      throws IOException {
    if (StringUtils.isBlank(srcPath)) {
      return;
    }

    File srcFile = new File(srcPath);
    File destFile = new File(destPath, srcFile.getName());

    if (!destFile.exists() || !FileUtils.contentEquals(srcFile, destFile)) {
      FileUtils.copyFile(srcFile, destFile);
      LOGGER.debug("copy {} to {}", srcFile.getAbsolutePath(), destPath);
    }
  }

  private List<File> loadFromMvn(String artifact, Collection<String> excludes)
      throws RepositoryException {
    Collection<String> allExclusions = new LinkedList<>();
    allExclusions.addAll(excludes);
    allExclusions.addAll(Arrays.asList(exclusions));

    List<ArtifactResult> listOfArtifact;
    listOfArtifact = getArtifactsWithDep(artifact, allExclusions);

    Iterator<ArtifactResult> it = listOfArtifact.iterator();
    while (it.hasNext()) {
      Artifact a = it.next().getArtifact();
      String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
      for (String exclude : allExclusions) {
        if (gav.startsWith(exclude)) {
          it.remove();
          break;
        }
      }
    }

    List<File> files = new LinkedList<>();
    for (ArtifactResult artifactResult : listOfArtifact) {
      files.add(artifactResult.getArtifact().getFile());
      LOGGER.debug("load {}", artifactResult.getArtifact().getFile().getAbsolutePath());
    }

    return files;
  }

  /**
   * @param dependency
   * @param excludes list of pattern can either be of the form groupId:artifactId
   * @return
   * @throws Exception
   */
  @Override
  public List<ArtifactResult> getArtifactsWithDep(String dependency,
                                                  Collection<String> excludes)
      throws RepositoryException {
    Artifact artifact = new DefaultArtifact(dependency);
    DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
    PatternExclusionsDependencyFilter exclusionFilter =
            new PatternExclusionsDependencyFilter(excludes);

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));

    synchronized (repos) {
      for (RemoteRepository repo : repos) {
        collectRequest.addRepository(repo);
      }
    }
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
            DependencyFilterUtils.andFilter(exclusionFilter, classpathFilter));
    try {
      return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
    } catch (NullPointerException | DependencyResolutionException ex) {
      throw new RepositoryException(
              String.format("Cannot fetch dependencies for %s", dependency), ex);
    }
  }
}
