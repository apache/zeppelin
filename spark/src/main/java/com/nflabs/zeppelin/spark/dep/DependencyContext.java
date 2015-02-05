package com.nflabs.zeppelin.spark.dep;

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
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.ExclusionsDependencyFilter;


/**
 *
 */
public class DependencyContext {
  List<Dependency> dependencies = new LinkedList<Dependency>();
  List<Repository> repositories = new LinkedList<Repository>();

  List<File> files = new LinkedList<File>();
  List<File> filesDist = new LinkedList<File>();
  private RepositorySystem system = Booter.newRepositorySystem();
  private RepositorySystemSession session = Booter.newRepositorySystemSession(system);
  private RemoteRepository mavenCentral = new RemoteRepository("central",
      "default", "http://repo1.maven.org/maven2/");
  private RemoteRepository mavenLocal = new RemoteRepository("local",
      "default", "file://" + System.getProperty("user.home") + "/.m2/repository");

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

    if (dep.isRecursive()) {
      DependencyFilter classpathFlter = DependencyFilterUtils
          .classpathFilter(JavaScopes.COMPILE);
      ExclusionsDependencyFilter exclusionFilter = new ExclusionsDependencyFilter(
          dep.getExclusions());

      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRoot(new org.sonatype.aether.graph.Dependency(artifact,
          JavaScopes.COMPILE));

      collectRequest.addRepository(mavenCentral);
      collectRequest.addRepository(mavenLocal);
      for (Repository repo : repositories) {
        RemoteRepository rr = new RemoteRepository(repo.getName(), "default", repo.getUrl());
        rr.setPolicy(repo.isSnapshot(), null);
        collectRequest.addRepository(rr);
      }

      DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
          DependencyFilterUtils.andFilter(exclusionFilter, classpathFlter));

      return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
    } else {
      ArtifactRequest artifactRequest = new ArtifactRequest();
      artifactRequest.setArtifact(artifact);

      artifactRequest.addRepository(mavenCentral);
      artifactRequest.addRepository(mavenLocal);
      for (Repository repo : repositories) {
        RemoteRepository rr = new RemoteRepository(repo.getName(), "default", repo.getUrl());
        rr.setPolicy(repo.isSnapshot(), null);
        artifactRequest.addRepository(rr);
      }

      ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
      LinkedList<ArtifactResult> results = new LinkedList<ArtifactResult>();
      results.add(artifactResult);
      return results;
    }
  }

  public List<File> getFiles() {
    return files;
  }

  public List<File> getFilesDist() {
    return filesDist;
  }
}
