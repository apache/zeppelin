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
import java.lang.reflect.Method
import java.net.URL
import java.util

import grizzled.slf4j.Logging
import org.apache.commons.lang.StringUtils
import org.apache.spark.SparkContext

import org.apache.zeppelin.dep.AbstractDependencyResolver

import org.sonatype.aether.artifact.Artifact
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.graph.{Dependency, DependencyFilter}
import org.sonatype.aether.resolution.{ArtifactResult, DependencyRequest}
import org.sonatype.aether.util.artifact.{JavaScopes, DefaultArtifact}
import org.sonatype.aether.util.filter.{PatternExclusionsDependencyFilter, DependencyFilterUtils}

import scala.collection.JavaConversions._
import scala.tools.nsc.Global
import scala.tools.nsc.backend.JavaPlatform
import scala.tools.nsc.interpreter.IMain

class SparkDependencyResolver(intp: IMain, sc: SparkContext, localRepoPath: String, additionalRemoteRepository: String) extends AbstractDependencyResolver(localRepoPath) with Logging {
  private var global: Global = intp.global
  private val exclusions: Array[String] = Array[String](
    "org.scala-lang:scala-library",
    "org.scala-lang:scala-compiler",
    "org.scala-lang:scala-reflect",
    "org.scala-lang:scalap",
    "org.apache.zeppelin:zeppelin-zengine",
    "org.apache.zeppelin:zeppelin-spark",
    "org.apache.zeppelin:zeppelin-server"
  )

  addRepoFromProperty(additionalRemoteRepository)

  private def addRepoFromProperty(listOfRepo: String) {
    if (listOfRepo != null) {
      val repos: Array[String] = listOfRepo.split(";")
      for (repo <- repos) {
        val parts: Array[String] = repo.split(",")
        if (parts.length == 3) {
          val id: String = parts(0).trim
          val url: String = parts(1).trim
          val isSnapshot: Boolean = java.lang.Boolean.parseBoolean(parts(2).trim)
          if (id.length > 1 && url.length > 1) {
            addRepo(id, url, isSnapshot)
          }
        }
      }
    }
  }

  private def updateCompilerClassPath(urls: Array[URL]): Unit = {
    val platform: JavaPlatform = global.platform.asInstanceOf[JavaPlatform]
    for (url <- urls) {
      platform.classPath.mergeUrlsIntoClassPath(url)
    }
    //TODO(ECH) do we miss something here?
  }

  private def updateRuntimeClassPath(urls: Array[URL]): Unit = {
    val cl: ClassLoader = intp.classLoader.getParent
    var addURL: Method = null
    addURL = cl.getClass.getDeclaredMethod("addNewUrl", classOf[URL])
    addURL.setAccessible(true)
    for (url <- urls) {
      addURL.invoke(cl, url)
    }
  }
/*
  private def mergeUrlsIntoClassPath(platform: JavaPlatform, urls: Array[URL]): MergedClassPath[AbstractFile] = {
    val entries: IndexedSeq[ClassPath[AbstractFile]] = (platform.classPath.asInstanceOf[MergedClassPath[AbstractFile]]).entries
    val cp: List[ClassPath[AbstractFile]] = new util.LinkedList[ClassPath[AbstractFile]]
    {
      var i: Int = 0
      while (i < entries.size) {
        {
          cp.add(entries.apply(i))
        }
        ({
          i += 1; i - 1
        })
      }
    }
    for (url <- urls) {
      var file: AbstractFile = null
      if ("file" == url.getProtocol) {
        val f: File = new File(url.getPath)
        if (f.isDirectory) {
          file = AbstractFile.getDirectory(scala.reflect.io.Path.jfile2path(f))
        }
        else {
          file = AbstractFile.getFile(scala.reflect.io.Path.jfile2path(f))
        }
      }
      else {
        file = AbstractFile.getURL(url)
      }
      val newcp: ClassPath[AbstractFile] = platform.classPath.context.newClassPath(file)
      if (cp.contains(newcp) == false) {
        cp.add(newcp)
      }
    }
    MergedClassPath[AbstractFile](scala.collection.JavaConversions.asScalaBuffer(cp).toIndexedSeq, platform.classPath.context)
  }
*/
  def load(artifact: String, addSparkContext: Boolean): util.List[String] = {
    return load(artifact, new util.LinkedList[String], addSparkContext)
  }

  def load(artifact: String, excludes: util.Collection[String], addSparkContext: Boolean): util.List[String] = {
    if (StringUtils.isBlank(artifact)) {
      throw new RuntimeException("Invalid artifact to load")
    }
    val numSplits: Int = artifact.split(":").length
    if (numSplits >= 3 && numSplits <= 6) {
      return loadFromMvn(artifact, excludes, addSparkContext)
    }
    else {
      loadFromFs(artifact, addSparkContext)
      val libs: util.LinkedList[String] = new util.LinkedList[String]
      libs.add(artifact)
      return libs
    }
  }

  private def loadFromFs(artifact: String, addSparkContext: Boolean) {
    val jarFile: File = new File(artifact)
    updateRuntimeClassPath(Array[URL](jarFile.toURI.toURL))
    if (addSparkContext) {
      sc.addJar(jarFile.getAbsolutePath)
    }
  }

  private def loadFromMvn(artifact: String, excludes: util.Collection[String], addSparkContext: Boolean): util.List[String] = {
    val loadedLibs: util.List[String] = new util.LinkedList[String]
    val allExclusions: util.Collection[String] = new util.LinkedList[String]
    allExclusions.addAll(excludes)
    allExclusions.addAll(exclusions.toList)
    var listOfArtifact: util.List[ArtifactResult] = null
    listOfArtifact = getArtifactsWithDep(artifact, allExclusions)
    val it: Iterator[ArtifactResult] = listOfArtifact.iterator
    while (it.hasNext) {
      val a: Artifact = it.next.getArtifact
      val gav: String = a.getGroupId + ":" + a.getArtifactId + ":" + a.getVersion
      for (exclude <- allExclusions) {
        if (gav.startsWith(exclude)) {
          it.remove
        }
      }
    }
    val newClassPathList: util.List[URL] = new util.LinkedList[URL]
    val files: util.List[File] = new util.LinkedList[File]
    for (artifactResult <- listOfArtifact) {
      logger.info("Load " + artifactResult.getArtifact.getGroupId + ":" + artifactResult.getArtifact.getArtifactId + ":" + artifactResult.getArtifact.getVersion)
      newClassPathList.add(artifactResult.getArtifact.getFile.toURI.toURL)
      files.add(artifactResult.getArtifact.getFile)
      loadedLibs.add(artifactResult.getArtifact.getGroupId + ":" + artifactResult.getArtifact.getArtifactId + ":" + artifactResult.getArtifact.getVersion)
    }
    updateRuntimeClassPath(newClassPathList.toArray(new Array[URL](0)))
    updateCompilerClassPath(newClassPathList.toArray(new Array[URL](0)))
    if (addSparkContext) {
      import scala.collection.JavaConversions._
      for (f <- files) {
        sc.addJar(f.getAbsolutePath)
      }
    }
    return loadedLibs
  }

  /**
   * @param dependency
   * @param excludes list of pattern can either be of the form groupId:artifactId
   * @return
   * @throws Exception
   */
  def getArtifactsWithDep(dependency: String, excludes: util.Collection[String]): util.List[ArtifactResult] = {
    val artifact: Artifact = new DefaultArtifact(inferScalaVersion(dependency))
    val classpathFilter: DependencyFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)
    val exclusionFilter: PatternExclusionsDependencyFilter = new PatternExclusionsDependencyFilter(inferScalaVersion(excludes))
    val collectRequest: CollectRequest = new CollectRequest
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE))
    repos synchronized {
      import scala.collection.JavaConversions._
      for (repo <- repos) {
        collectRequest.addRepository(repo)
      }
    }
    val dependencyRequest: DependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.andFilter(exclusionFilter, classpathFilter))
    system.resolveDependencies(session, dependencyRequest).getArtifactResults
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
