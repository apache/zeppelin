package com.nflabs.zeppelin.spark.dep;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkILoop;
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

import scala.Some;
import scala.collection.IndexedSeq;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Global;
import scala.tools.nsc.backend.JavaPlatform;
import scala.tools.nsc.util.ClassPath;
import scala.tools.nsc.util.MergedClassPath;

public class DependencyResolver {
	Logger logger = LoggerFactory.getLogger(DependencyResolver.class);
	private Global global;
	private SparkContext sc;
	private RepositorySystem system = Booter.newRepositorySystem();
	private RemoteRepository repo = Booter.newCentralRepository();
	private RepositorySystemSession session = Booter.newRepositorySystemSession(system);
	private DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
	                                                                                JavaScopes.PROVIDED,
	                                                                                JavaScopes.RUNTIME,
	                                                                                JavaScopes.SYSTEM);


	public DependencyResolver(Global global, SparkContext sc){
		this.global = global;
		this.sc = sc;
	}
	

	private void updateCompilerClassPath(URL [] urls) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		JavaPlatform platform = (JavaPlatform) global.platform();
		MergedClassPath<AbstractFile> newClassPath = mergeUrlsIntoClassPath(platform, urls);
		
		Method[] methods = platform.getClass().getMethods();
		for(Method m : methods) {
			if(m.getName().endsWith("currentClassPath_$eq")){
				m.invoke(platform, new Some(newClassPath));
				break;
			}
		}

        // NOTE: Must use reflection until this is exposed/fixed upstream in Scala
		List<String> classPaths = new LinkedList<String>();
		for(URL url : urls) {
			classPaths.add(url.getPath());
		}		

        // Reload all jars specified into our compiler
		global.invalidateClassPathEntries(scala.collection.JavaConversions.asScalaBuffer(classPaths).toList());
	}
	
	private MergedClassPath<AbstractFile> mergeUrlsIntoClassPath(JavaPlatform platform, URL[] urls) {
		IndexedSeq<ClassPath<AbstractFile>> entries = ((MergedClassPath<AbstractFile>) platform.classPath()).entries();
		List<ClassPath<AbstractFile>> cp = new LinkedList<ClassPath<AbstractFile>>();
		
		for(int i=0; i<entries.size(); i++){
			cp.add(entries.apply(i));
		}
		
		for(URL url : urls){
			AbstractFile file;
			if("file".equals(url.getProtocol())){				
				File f = new File(url.getPath());
				if(f.isDirectory()){
					file = AbstractFile.getDirectory(scala.reflect.io.File.jfile2path(f));
				} else {
					file = AbstractFile.getFile(scala.reflect.io.File.jfile2path(f));
				}
			} else {
				file = AbstractFile.getURL(url);
			}
			
			ClassPath<AbstractFile> newcp = platform.classPath().context().newClassPath(file);
			
			// distinct			
			if(cp.contains(newcp)==false){
				cp.add(newcp);
			}
		}
		
		return new MergedClassPath(scala.collection.JavaConversions.asScalaBuffer(cp).toIndexedSeq(), platform.classPath().context());
	}

	public void load(String groupId, String artifactId, String version) {
		if (StringUtils.isBlank(groupId) || StringUtils.isBlank(artifactId) || StringUtils.isBlank(version)) {
			// Should throw here
			return;
		}
		load(groupId + ":" + artifactId + ":" + version);
	}

	public void load(String artifact) {
		if (StringUtils.isBlank(artifact)) {
			// Should throw here
			return;
		}

		try {
			List<ArtifactResult> listOfArtifact = getArtifactsFor(artifact);
			List<URL> newClassPathList = new LinkedList<URL>();
			List<File> files = new LinkedList<File>();
			for (ArtifactResult artifactResult : listOfArtifact) {
				newClassPathList.add(artifactResult.getArtifact().getFile().toURI().toURL());
				files.add(artifactResult.getArtifact().getFile());
			}
			
			updateCompilerClassPath(newClassPathList.toArray(new URL[0]));

			for(File f : files) {
				sc.addJar(f.getAbsolutePath());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<ArtifactResult> getArtifactsFor(String dependency)
			throws Exception {
		Artifact artifact = new DefaultArtifact(dependency);
		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
		collectRequest.addRepository(repo);
		DependencyRequest dependencyRequest = new DependencyRequest(
				collectRequest, classpathFlter);
		return system.resolveDependencies(session, dependencyRequest)
				.getArtifactResults();
	}

	private void createSharedFolder() {
		File sharedDirectory = new File("/tmp/imported");
		if (!sharedDirectory.exists()) {
			sharedDirectory.mkdir();
		}
	}
}
