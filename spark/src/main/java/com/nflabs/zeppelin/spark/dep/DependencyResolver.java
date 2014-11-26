package com.nflabs.zeppelin.spark.dep;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkIMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;

import scala.Some;
import scala.collection.IndexedSeq;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Global;
import scala.tools.nsc.backend.JavaPlatform;
import scala.tools.nsc.interpreter.AbstractFileClassLoader;
import scala.tools.nsc.util.ClassPath;
import scala.tools.nsc.util.MergedClassPath;

/**
 * Deps resolver.
 * Add new dependencies from mvn repo (at runetime) to Zeppelin.
 * 
 * @author anthonycorbacho
 *
 */
public class DependencyResolver {
  Logger logger = LoggerFactory.getLogger(DependencyResolver.class);
  private Global global;
  private SparkIMain intp;
  private SparkContext sc;
  private RepositorySystem system = Booter.newRepositorySystem();
  private RemoteRepository repo = Booter.newCentralRepository();
  private RepositorySystemSession session = Booter.newRepositorySystemSession(system);
  private DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(
                                                                                JavaScopes.COMPILE,
                                                                                JavaScopes.PROVIDED,
                                                                                JavaScopes.RUNTIME,
                                                                                JavaScopes.SYSTEM);

  private final String[] exclusions = new String[] {"org.scala-lang:scala-library",
                                                    "org.scala-lang:scala-compiler",
                                                    "com.nflabs.zeppelin:zeppelin-zengine",
                                                    "com.nflabs.zeppelin:zeppelin-spark",
                                                    "com.nflabs.zeppelin:zeppelin-server"};

  public DependencyResolver(SparkIMain intp, SparkContext sc) {
    this.intp = intp;
    this.global = intp.global();
    this.sc = sc;
  }

  private void updateCompilerClassPath(URL[] urls) throws IllegalAccessException,
      IllegalArgumentException, InvocationTargetException {

    JavaPlatform platform = (JavaPlatform) global.platform();
    MergedClassPath<AbstractFile> newClassPath = mergeUrlsIntoClassPath(platform, urls);

    Method[] methods = platform.getClass().getMethods();
    for (Method m : methods) {
      if (m.getName().endsWith("currentClassPath_$eq")) {
        m.invoke(platform, new Some(newClassPath));
        break;
      }
    }

    // NOTE: Must use reflection until this is exposed/fixed upstream in Scala
    List<String> classPaths = new LinkedList<String>();
    for (URL url : urls) {
      classPaths.add(url.getPath());
    }

    // Reload all jars specified into our compiler
    global.invalidateClassPathEntries(scala.collection.JavaConversions.asScalaBuffer(classPaths)
        .toList());
  }

  // Until spark 1.1.x
  // check https://github.com/apache/spark/commit/191d7cf2a655d032f160b9fa181730364681d0e7
  private void updateRuntimeClassPath(URL[] urls) throws SecurityException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
    ClassLoader cl = intp.classLoader().getParent();
    Method addURL;
    addURL = cl.getClass().getDeclaredMethod("addURL", new Class[] {URL.class});
    addURL.setAccessible(true);
    for (URL url : urls) {
      addURL.invoke(cl, url);
    }
  }

  private MergedClassPath<AbstractFile> mergeUrlsIntoClassPath(JavaPlatform platform, URL[] urls) {
    IndexedSeq<ClassPath<AbstractFile>> entries =
        ((MergedClassPath<AbstractFile>) platform.classPath()).entries();
    List<ClassPath<AbstractFile>> cp = new LinkedList<ClassPath<AbstractFile>>();

    for (int i = 0; i < entries.size(); i++) {
      cp.add(entries.apply(i));
    }

    for (URL url : urls) {
      AbstractFile file;
      if ("file".equals(url.getProtocol())) {
        File f = new File(url.getPath());
        if (f.isDirectory()) {
          file = AbstractFile.getDirectory(scala.reflect.io.File.jfile2path(f));
        } else {
          file = AbstractFile.getFile(scala.reflect.io.File.jfile2path(f));
        }
      } else {
        file = AbstractFile.getURL(url);
      }

      ClassPath<AbstractFile> newcp = platform.classPath().context().newClassPath(file);

      // distinct
      if (cp.contains(newcp) == false) {
        cp.add(newcp);
      }
    }

    return new MergedClassPath(scala.collection.JavaConversions.asScalaBuffer(cp).toIndexedSeq(),
        platform.classPath().context());
  }

  public void load(String groupId, String artifactId, String version, boolean recursive,
      boolean addSparkContext) throws Exception {
    if (StringUtils.isBlank(groupId) || StringUtils.isBlank(artifactId)
        || StringUtils.isBlank(version)) {
      // Should throw here
      return;
    }
    load(groupId + ":" + artifactId + ":" + version, recursive, addSparkContext);
  }

  public void load(String artifact, boolean recursive, boolean addSparkContext) throws Exception {
    if (StringUtils.isBlank(artifact)) {
      // Should throw here
      return;
    }

    if (artifact.split(":").length == 3) {
      loadFromMvn(artifact, recursive, addSparkContext);
    } else {
      loadFromFs(artifact, addSparkContext);
    }
  }

  private void loadFromFs(String artifact, boolean addSparkContext) throws Exception {
    File jarFile = new File(artifact);

    updateCompilerClassPath(new URL[] {jarFile.toURI().toURL()});
    updateRuntimeClassPath(new URL[] {jarFile.toURI().toURL()});

    if (addSparkContext) {
      sc.addJar(jarFile.getAbsolutePath());
    }
  }

  private void loadFromMvn(String artifact, boolean recursive, boolean addSparkContext)
      throws Exception {
    List<ArtifactResult> listOfArtifact;
    if (recursive) {
      listOfArtifact = getArtifactsWithDep(artifact);
    } else {
      listOfArtifact = getArtifact(artifact);
    }

    Iterator<ArtifactResult> it = listOfArtifact.iterator();
    while (it.hasNext()) {
      Artifact a = it.next().getArtifact();
      String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
      for (String exclude : exclusions) {
        if (gav.startsWith(exclude)) {
          it.remove();
          break;
        }
      }
    }

    List<URL> newClassPathList = new LinkedList<URL>();
    List<File> files = new LinkedList<File>();
    for (ArtifactResult artifactResult : listOfArtifact) {
      logger.info("Load " + artifactResult.getArtifact().getGroupId() + ":"
          + artifactResult.getArtifact().getArtifactId() + ":"
          + artifactResult.getArtifact().getVersion());
      newClassPathList.add(artifactResult.getArtifact().getFile().toURI().toURL());
      files.add(artifactResult.getArtifact().getFile());
    }

    updateCompilerClassPath(newClassPathList.toArray(new URL[0]));
    updateRuntimeClassPath(newClassPathList.toArray(new URL[0]));

    if (addSparkContext) {
      for (File f : files) {
        sc.addJar(f.getAbsolutePath());
      }
    }
  }

  public List<ArtifactResult> getArtifact(String dependency) throws Exception {
    Artifact artifact = new DefaultArtifact(dependency);
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    artifactRequest.addRepository(repo);

    ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
    LinkedList<ArtifactResult> results = new LinkedList<ArtifactResult>();
    results.add(artifactResult);
    return results;
  }

  public List<ArtifactResult> getArtifactsWithDep(String dependency) throws Exception {
    Artifact artifact = new DefaultArtifact(dependency);
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
    collectRequest.addRepository(repo);
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);
    return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
  }
}
