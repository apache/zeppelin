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
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;


/**
 * Deps resolver.
 * Add new dependencies from mvn repo (at runtime) to Zeppelin.
 */
public class DependencyResolver extends AbstractDependencyResolver {
  Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

  private final String[] exclusions = new String[] {"org.apache.zeppelin:zeppelin-zengine",
                                                    "org.apache.zeppelin:zeppelin-interpreter",
                                                    "org.apache.zeppelin:zeppelin-server"};

  public DependencyResolver(String localRepoPath) {
    super(localRepoPath);
  }

  public List<File> load(String artifact)
      throws RepositoryException, IOException {
    return load(artifact, new LinkedList<String>());
  }
  
  public List<File> load(String artifact, String destPath)
      throws RepositoryException, IOException {
    return load(artifact, new LinkedList<String>(), destPath);
  }

  public synchronized List<File> load(String artifact, Collection<String> excludes)
      throws RepositoryException, IOException {
    if (StringUtils.isBlank(artifact)) {
      // Skip dependency loading if artifact is empty
      return new LinkedList<File>();
    }

    // <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
    int numSplits = artifact.split(":").length;
    if (numSplits >= 3 && numSplits <= 6) {
      return loadFromMvn(artifact, excludes);
    } else {
      LinkedList<File> libs = new LinkedList<File>();
      libs.add(new File(artifact));
      return libs;
    }
  }
  
  public List<File> load(String artifact, Collection<String> excludes, String destPath)
      throws RepositoryException, IOException {
    List<File> libs = new LinkedList<File>();

    if (StringUtils.isNotBlank(artifact)) {
      libs = load(artifact, excludes);

      // find home dir
      String home = System.getenv("ZEPPELIN_HOME");
      if (home == null) {
        home = System.getProperty("zeppelin.home");
      }
      if (home == null) {
        home = "..";
      }

      for (File srcFile : libs) {
        File destFile = new File(home + "/" + destPath, srcFile.getName());
        if (!destFile.exists() || !FileUtils.contentEquals(srcFile, destFile)) {
          FileUtils.copyFile(srcFile, destFile);
          logger.info("copy {} to {}", srcFile.getAbsolutePath(), destPath);
        }
      }
    }
    return libs;
  }

  private List<File> loadFromMvn(String artifact, Collection<String> excludes)
      throws RepositoryException {
    Collection<String> allExclusions = new LinkedList<String>();
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

    List<File> files = new LinkedList<File>();
    for (ArtifactResult artifactResult : listOfArtifact) {
      files.add(artifactResult.getArtifact().getFile());
      logger.info("load {}", artifactResult.getArtifact().getFile().getAbsolutePath());
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
      Collection<String> excludes) throws RepositoryException {
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
    return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
  }
}
