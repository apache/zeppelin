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

package org.apache.zeppelin.spark2.dep

import java.io.File
import java.net.MalformedURLException
import java.util
import java.util.LinkedList
import java.util.List
import org.apache.zeppelin.dep.Booter
import org.apache.zeppelin.dep.Dependency
import org.apache.zeppelin.dep.Repository
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.artifact.Artifact
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.graph.DependencyFilter
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.repository.Authentication
import org.sonatype.aether.resolution.ArtifactResolutionException
import org.sonatype.aether.resolution.ArtifactResult
import org.sonatype.aether.resolution.DependencyRequest
import org.sonatype.aether.resolution.DependencyResolutionException
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.util.artifact.JavaScopes
import org.sonatype.aether.util.filter.DependencyFilterUtils
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter

import scala.collection.JavaConversions._

/**
 *
 */
class SparkDependencyContext {
  private[dep] var dependencies: List[Dependency] = new LinkedList[Dependency]
  private[dep] var repositories: List[Repository] = new LinkedList[Repository]
  private[dep] var files: List[File] = new LinkedList[File]
  private[dep] var filesDist: List[File] = new LinkedList[File]
  private var system: RepositorySystem = Booter.newRepositorySystem
  private var session: RepositorySystemSession = null
  private var mavenCentral: RemoteRepository = Booter.newCentralRepository
  private var mavenLocal: RemoteRepository = Booter.newLocalRepository
  private var additionalRepos: List[RemoteRepository] = new LinkedList[RemoteRepository]

  def this(localRepoPath: String, additionalRemoteRepository: String) {
    this()
    session = Booter.newRepositorySystemSession(system, localRepoPath)
    addRepoFromProperty(additionalRemoteRepository)
  }

  def load(lib: String): Dependency = {
    Console.println("DepInterpreter(%dep) deprecated. " + "Load dependency through GUI interpreter menu instead.")
    val dep: Dependency = new Dependency(lib)
    if (dependencies.contains(dep)) {
      dependencies.remove(dep)
    }
    dependencies.add(dep)
    dep
  }

  def addRepo(name: String): Repository = {
    Console.println("DepInterpreter(%dep) deprecated. " + "Add repository through GUI interpreter menu instead.")
    val rep: Repository = new Repository(name)
    repositories.add(rep)
    rep
  }

  def reset(): Unit = {
    /*
    TODO(ECH)
    Console.println("DepInterpreter(%dep) deprecated. " + "Remove dependencies and repositories through GUI interpreter menu instead.")
    dependencies = new LinkedList[Dependency]
    repositories = new LinkedList[Repository]
    files = new LinkedList[File]
    filesDist = new LinkedList[File]
    */
  }

  private def addRepoFromProperty(listOfRepo: String): Unit = {
    if (listOfRepo != null) {
      val repos: Array[String] = listOfRepo.split(";")
      for (repo <- repos) {
        val parts: Array[String] = repo.split(",")
        if (parts.length == 3) {
          val id: String = parts(0).trim
          val url: String = parts(1).trim
          val isSnapshot: Boolean = java.lang.Boolean.parseBoolean(parts(2).trim)
          if (id.length > 1 && url.length > 1) {
            val rr: RemoteRepository = new RemoteRepository(id, "default", url)
            rr.setPolicy(isSnapshot, null)
            additionalRepos.add(rr)
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
  def fetch: List[File] = {
    import scala.collection.JavaConversions._
    for (dep <- dependencies) {
      if (!dep.isLocalFsArtifact) {
        val artifacts: List[ArtifactResult] = fetchArtifactWithDep(dep)
        import scala.collection.JavaConversions._
        for (artifact <- artifacts) {
          if (dep.isDist) {
            filesDist.add(artifact.getArtifact.getFile)
          }
          files.add(artifact.getArtifact.getFile)
        }
      }
      else {
        if (dep.isDist) {
          filesDist.add(new File(dep.getGroupArtifactVersion))
        }
        files.add(new File(dep.getGroupArtifactVersion))
      }
    }
    files
  }

  private def fetchArtifactWithDep(dep: Dependency): List[ArtifactResult] = {
    val artifact: Artifact = new DefaultArtifact(inferScalaVersion(dep.getGroupArtifactVersion))
    val classpathFlter: DependencyFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)
    val exclusionFilter: PatternExclusionsDependencyFilter = new PatternExclusionsDependencyFilter(inferScalaVersion(dep.getExclusions))
    val collectRequest: CollectRequest = new CollectRequest
    collectRequest.setRoot(new org.sonatype.aether.graph.Dependency(artifact, JavaScopes.COMPILE))
    collectRequest.addRepository(mavenCentral)
    collectRequest.addRepository(mavenLocal)
    import scala.collection.JavaConversions._
    for (repo <- additionalRepos) {
      collectRequest.addRepository(repo)
    }
    import scala.collection.JavaConversions._
    for (repo <- repositories) {
      val rr: RemoteRepository = new RemoteRepository(repo.getId, "default", repo.getUrl)
      rr.setPolicy(repo.isSnapshot, null)
      val auth: Authentication = repo.getAuthentication
      if (auth != null) {
        rr.setAuthentication(auth)
      }
      collectRequest.addRepository(rr)
    }
    val dependencyRequest: DependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.andFilter(exclusionFilter, classpathFlter))
    return system.resolveDependencies(session, dependencyRequest).getArtifactResults
  }

  def getFiles: List[File] = {
    return files
  }

  def getFilesDist: List[File] = {
    return filesDist
  }

  def inferScalaVersion(artifact: util.Collection[String]): util.Collection[String] = {
    val list: util.List[String] = new util.LinkedList[String]
    for (a <- artifact) {
      list.add(inferScalaVersion(a))
    }
    list
  }

  def inferScalaVersion(artifact: String): String = {
    val pos: Int = artifact.indexOf(":")
    if (pos < 0 || pos + 2 >= artifact.length) {
      return artifact
    }
    if (':' == artifact.charAt(pos + 1)) {
      var restOfthem: String = ""
      var versionSep: String = ":"
      val groupId: String = artifact.substring(0, pos)
      var nextPos: Int = artifact.indexOf(":", pos + 2)
      if (nextPos < 0) {
        if (artifact.charAt(artifact.length - 1) == '*') {
          nextPos = artifact.length - 1
          versionSep = ""
          restOfthem = "*"
        }
        else {
          versionSep = ""
          nextPos = artifact.length
        }
      }
      val artifactId: String = artifact.substring(pos + 2, nextPos)
      if (nextPos < artifact.length) {
        if (!(restOfthem == "*")) {
          restOfthem = artifact.substring(nextPos + 1)
        }
      }
      val version: Array[String] = scala.util.Properties.versionNumberString.split("[.]")
      val scalaVersion: String = version(0) + "." + version(1)
      return groupId + ":" + artifactId + "_" + scalaVersion + versionSep + restOfthem
    }
    else {
      return artifact
    }
  }

}
