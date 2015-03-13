package com.nflabs.zeppelin.spark.dep;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
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
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;

import scala.Some;
import scala.collection.IndexedSeq;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Global;
import scala.tools.nsc.backend.JavaPlatform;
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
  private List<RemoteRepository> repos = new LinkedList<RemoteRepository>();
  private RepositorySystemSession session;
  private DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(
                                                                                JavaScopes.COMPILE,
                                                                                JavaScopes.PROVIDED,
                                                                                JavaScopes.RUNTIME,
                                                                                JavaScopes.SYSTEM);

  private final String[] exclusions = new String[] {"org.scala-lang:scala-library",
                                                    "org.scala-lang:scala-compiler",
                                                    "org.scala-lang:scala-reflect",
                                                    "org.scala-lang:scalap",
                                                    "com.nflabs.zeppelin:zeppelin-zengine",
                                                    "com.nflabs.zeppelin:zeppelin-spark",
                                                    "com.nflabs.zeppelin:zeppelin-server"};

  public DependencyResolver(SparkIMain intp, SparkContext sc, String localRepoPath) {
    this.intp = intp;
    this.global = intp.global();
    this.sc = sc;
    session = Booter.newRepositorySystemSession(system, localRepoPath);
    repos.add(Booter.newCentralRepository()); // add maven central
    repos.add(new RemoteRepository("local", "default", "file://"
        + System.getProperty("user.home") + "/.m2/repository"));
  }

  public void addRepo(String id, String url, boolean snapshot) {
    synchronized (repos) {
      delRepo(id);
      RemoteRepository rr = new RemoteRepository(id, "default", url);
      rr.setPolicy(snapshot, null);
      repos.add(rr);
    }
  }

  public RemoteRepository delRepo(String id) {
    synchronized (repos) {
      Iterator<RemoteRepository> it = repos.iterator();
      if (it.hasNext()) {
        RemoteRepository repo = it.next();
        if (repo.getId().equals(id)) {
          it.remove();
          return repo;
        }
      }
    }
    return null;
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

  public List<String> load(String artifact,
      boolean addSparkContext) throws Exception {
    return load(artifact, new LinkedList<String>(), addSparkContext);
  }

  public List<String> load(String artifact, Collection<String> excludes,
      boolean addSparkContext) throws Exception {
    if (StringUtils.isBlank(artifact)) {
      // Should throw here
      throw new RuntimeException("Invalid artifact to load");
    }

    // <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
    int numSplits = artifact.split(":").length;
    if (numSplits >= 3 && numSplits <= 6) {
      return loadFromMvn(artifact, excludes, addSparkContext);
    } else {
      loadFromFs(artifact, addSparkContext);
      LinkedList<String> libs = new LinkedList<String>();
      libs.add(artifact);
      return libs;
    }
  }

  private void loadFromFs(String artifact, boolean addSparkContext) throws Exception {
    File jarFile = new File(artifact);

    intp.global().new Run();

    updateRuntimeClassPath(new URL[] {jarFile.toURI().toURL()});
    updateCompilerClassPath(new URL[] {jarFile.toURI().toURL()});

    if (addSparkContext) {
      sc.addJar(jarFile.getAbsolutePath());
    }
  }

  private List<String> loadFromMvn(String artifact, Collection<String> excludes,
      boolean addSparkContext) throws Exception {
    List<String> loadedLibs = new LinkedList<String>();
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

    List<URL> newClassPathList = new LinkedList<URL>();
    List<File> files = new LinkedList<File>();
    for (ArtifactResult artifactResult : listOfArtifact) {
      logger.info("Load " + artifactResult.getArtifact().getGroupId() + ":"
          + artifactResult.getArtifact().getArtifactId() + ":"
          + artifactResult.getArtifact().getVersion());
      newClassPathList.add(artifactResult.getArtifact().getFile().toURI().toURL());
      files.add(artifactResult.getArtifact().getFile());
      loadedLibs.add(artifactResult.getArtifact().getGroupId() + ":"
          + artifactResult.getArtifact().getArtifactId() + ":"
          + artifactResult.getArtifact().getVersion());
    }

    intp.global().new Run();
    updateRuntimeClassPath(newClassPathList.toArray(new URL[0]));
    updateCompilerClassPath(newClassPathList.toArray(new URL[0]));

    if (addSparkContext) {
      for (File f : files) {
        sc.addJar(f.getAbsolutePath());
      }
    }

    return loadedLibs;
  }

  /**
   *
   * @param dependency
   * @param excludes list of pattern can either be of the form groupId:artifactId
   * @return
   * @throws Exception
   */
  public List<ArtifactResult> getArtifactsWithDep(String dependency,
      Collection<String> excludes) throws Exception {
    Artifact artifact = new DefaultArtifact(inferScalaVersion(dependency));
    DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter( JavaScopes.COMPILE );
    PatternExclusionsDependencyFilter exclusionFilter =
        new PatternExclusionsDependencyFilter(inferScalaVersion(excludes));

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));

    synchronized (repos) {
      for (RemoteRepository repo : repos) {
        collectRequest.addRepository(repo);
      }
    }
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
        DependencyFilterUtils.andFilter(exclusionFilter, classpathFlter));
    return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
  }

  public static Collection<String> inferScalaVersion(Collection<String> artifact) {
    List<String> list = new LinkedList<String>();
    for (String a : artifact) {
      list.add(inferScalaVersion(a));
    }
    return list;
  }

  public static String inferScalaVersion(String artifact) {
    int pos = artifact.indexOf(":");
    if (pos < 0 || pos + 2 >= artifact.length()) {
      // failed to infer
      return artifact;
    }

    if (':' == artifact.charAt(pos + 1)) {
      String restOfthem = "";
      String versionSep = ":";

      String groupId = artifact.substring(0, pos);
      int nextPos = artifact.indexOf(":", pos + 2);
      if (nextPos < 0) {
        if (artifact.charAt(artifact.length() - 1) == '*') {
          nextPos = artifact.length() - 1;
          versionSep = "";
          restOfthem = "*";
        } else {
          versionSep = "";
          nextPos = artifact.length();
        }
      }

      String artifactId = artifact.substring(pos + 2, nextPos);
      if (nextPos < artifact.length()) {
        if (!restOfthem.equals("*")) {
          restOfthem = artifact.substring(nextPos + 1);
        }
      }

      String [] version = scala.util.Properties.versionNumberString().split("[.]");
      String scalaVersion = version[0] + "." + version[1];

      return groupId + ":" + artifactId + "_" + scalaVersion + versionSep + restOfthem;
    } else {
      return artifact;
    }
  }
}
