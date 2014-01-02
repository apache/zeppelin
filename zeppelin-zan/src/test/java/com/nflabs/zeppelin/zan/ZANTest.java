package com.nflabs.zeppelin.zan;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZANTest {
	private File tmpDir;
	private FileSystem dfs;
	private Git git;
	private File zanrepoDir;
	private File lib1repoDir;
	private File lib2repoDir;

	@Before
	public void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZANTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();		
		dfs = FileSystem.get(new org.apache.hadoop.conf.Configuration());
		
		// create zan repo
		zanrepoDir = new File(tmpDir.getAbsolutePath()+"/local/zanrepo");

		FileRepositoryBuilder builder = new FileRepositoryBuilder();		
		FileRepository repo = builder.setGitDir(new File(zanrepoDir, ".git"))
									 .build();		
		repo.create();
		repo.close();
		
		// create repo for lib1
		lib1repoDir = new File(tmpDir.getAbsolutePath()+"/local/lib1repo");

		builder = new FileRepositoryBuilder();		
		repo = builder.setGitDir(new File(lib1repoDir, ".git"))
									 .build();		
		repo.create();
		repo.close();
		
		git = Git.open(lib1repoDir);
		FileUtils.writeStringToFile(new File(lib1repoDir, "zql"), "select * from table1");
		git.add().addFilepattern("zql").call();
		RevCommit cm1 = git.commit().setMessage("add zql").call();
		git.push();
		
		// create repo for lib2
		lib2repoDir = new File(tmpDir.getAbsolutePath()+"/local/lib2repo");

		builder = new FileRepositoryBuilder();
		repo = builder.setGitDir(new File(lib2repoDir, ".git"))
									 .build();
		repo.create();
		repo.close();
		
		git = Git.open(lib2repoDir);
		FileUtils.writeStringToFile(new File(lib2repoDir, "zql"), "select * from table2");
		git.add().addFilepattern("zql").call();
		RevCommit cm2 = git.commit().setMessage("add zql").call();
		git.push();
		
		// publish first library
		git = Git.open(zanrepoDir);
		File lib1Dir = new File(zanrepoDir, "lib1");
		lib1Dir.mkdir();		
		new Meta("file://"+lib1repoDir.getAbsolutePath(), "master", cm1.getName()).write(new File(lib1Dir, "meta"));
		git.add().addFilepattern("lib1").call();
		RevCommit rc = git.commit().setMessage("lib1 commit").call();
		git.push();
		
		// publish second library
		File lib2Dir = new File(zanrepoDir, "lib2");
		lib2Dir.mkdir();
		new Meta("file://"+lib2repoDir.getAbsolutePath(), "master", cm2.getName()).write(new File(lib2Dir, "meta"));		
		git.add().addFilepattern("lib2").call();
		git.commit().setMessage("lib2 commit").call();
		git.push();
		
	}

	@After
	public void tearDown() throws Exception {
		delete(tmpDir);
	}

	public static void delete(File file){
		if(file.isFile()) file.delete();
		else if(file.isDirectory()){
			File [] files = file.listFiles();
			if(files!=null && files.length>0){
				for(File f : files){
					delete(f);
				}
			}
			file.delete();
		}
	}
	
	@Test
	public void testSync() throws IOException, ZANException {		
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("", localBase, remoteBase, dfs);

		// create empty library
		new File(localBase+"/libname").mkdirs();
		Map<String, List<String>> changes = zan.sync("libname");
		assertEquals(1, changes.get("added").size());
		assertTrue(dfs.exists(new Path(remoteBase+"/libname")));
		
		// add a file to local
		stringToFile(localBase+"/libname/file", "content");
		changes = zan.sync("libname");
		assertEquals(1, changes.get("added").size());
		assertEquals(0, changes.get("modified").size());
		assertEquals(0, changes.get("removed").size());
		assertTrue(dfs.isFile(new Path(remoteBase+"/libname/file")));
		
		// sync without update
		changes = zan.sync("libname");
		assertEquals(0, changes.get("added").size());
		assertEquals(0, changes.get("modified").size());
		assertEquals(0, changes.get("removed").size());
		
		// add sub dir and a file
		new File(localBase+"/libname/sub").mkdirs();
		stringToFile(localBase+"/libname/sub/file", "content");
		changes = zan.sync("libname");
		assertEquals(2, changes.get("added").size());
		assertEquals(0, changes.get("modified").size());
		assertEquals(0, changes.get("removed").size());
		
		// delete a file
		new File(localBase+"/libname/file").delete();
		changes = zan.sync("libname");
		assertEquals(0, changes.get("added").size());
		assertEquals(0, changes.get("modified").size());
		assertEquals(1, changes.get("removed").size());
		assertFalse(dfs.isFile(new Path(remoteBase+"/libname/file")));
		
	}
	
	
	private void stringToFile(String path, String msg) throws IOException{
		FSDataOutputStream out = dfs.create(new Path(path));
		out.write(msg.getBytes());
		out.close();
	}
	
	@Test
	public void testUpdate() throws IOException, NoFilepatternException, GitAPIException, ZANException{
		// Create ZAN
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("file://"+zanrepoDir.getAbsolutePath(), localBase, remoteBase, dfs);
		
		zan.update(null);
		assertTrue(new File(localBase+"/.zan/lib1/meta").isFile());
		
		// push second library
		File lib3Dir = new File(zanrepoDir, "lib3");
		lib3Dir.mkdir();
		stringToFile(lib3Dir.getAbsolutePath()+"/meta", "meta3\n");
		
		git.add().addFilepattern("lib3").call();
		git.commit().setMessage("lib3 commit").call();
		git.push();
		
		// update
		zan.update(null);
		assertTrue(new File(localBase+"/.zan/lib3/meta").isFile());				
	}
	
	@Test
	public void testInstall() throws ZANException{
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("file://"+zanrepoDir.getAbsolutePath(), localBase, remoteBase, dfs);
		zan.update();
		
		// try to install lib1
		assertFalse(new File(localBase, "lib1").isDirectory());		
		zan.install("lib1", null);
		assertTrue(new File(localBase, "lib1").isDirectory());		
		assertTrue(new File(localBase, "lib1/zql").isFile());
		assertTrue(new File(remoteBase, "lib1").isDirectory());		
		assertTrue(new File(remoteBase, "lib1/zql").isFile());

		// try to install nonexist lib
		try {
			zan.install("nonexist", null);
			assertTrue(false);
		} catch (Exception e){			
		}
		assertFalse(new File(localBase, "nonexist").isDirectory());
		assertFalse(new File(remoteBase, "nonexist").isDirectory());
	}
	
	@Test
	public void testUpgrade() throws ZANException, IOException, NoFilepatternException, GitAPIException{
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("file://"+zanrepoDir.getAbsolutePath(), localBase, remoteBase, dfs);
		zan.update();
		
		// update library
		git = Git.open(lib1repoDir);
		FileUtils.writeStringToFile(new File(lib1repoDir, "res"), "res");
		git.add().addFilepattern("res").call();
		RevCommit cm = git.commit().setMessage("add resources").call();
		git.push();
				
		// install library. however, update is not yet published
		assertEquals(Info.Status.UNINSTALLED, zan.info("lib1").getStatus());
		zan.install("lib1", null);
		assertTrue(new File(localBase, "lib1").isDirectory());
		assertTrue(new File(localBase, "lib1/zql").isFile());
		assertFalse(new File(localBase, "lib1/res").isFile());
		assertEquals(Info.Status.INSTALLED, zan.info("lib1").getStatus());
		
		// publish update
		git = Git.open(zanrepoDir);
		new Meta("file://"+lib1repoDir.getAbsolutePath(), "master", cm.getName()).write(new File(zanrepoDir, "lib1/meta"));
		git.commit().setAll(true).setMessage("update lib1 commit").call();
		git.push();
		
		zan.update();
		assertEquals(Info.Status.UPDATEAVAILABLE, zan.info("lib1").getStatus());
		zan.upgrade("lib1", null);
		assertEquals(Info.Status.INSTALLED, zan.info("lib1").getStatus());
		assertTrue(new File(localBase, "lib1").isDirectory());
		assertTrue(new File(localBase, "lib1/zql").isFile());
		assertTrue(new File(localBase, "lib1/res").isFile());		
	}
	
	
	@Test
	public void testDelete() throws ZANException, IOException, NoFilepatternException, GitAPIException{
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("file://"+zanrepoDir.getAbsolutePath(), localBase, remoteBase, dfs);
		zan.update();
		
		// update library
		git = Git.open(lib1repoDir);
		FileUtils.writeStringToFile(new File(lib1repoDir, "res"), "res");
		git.add().addFilepattern("res").call();
		RevCommit cm = git.commit().setMessage("add resources").call();
		git.push();
				
		// install library. however, update is not yet published
		zan.install("lib1", null);
		zan.install("lib2", null);
		
		assertTrue(new File(localBase, "lib1").isDirectory());
		assertTrue(new File(localBase, "lib2").isDirectory());
		assertTrue(new File(remoteBase, "lib1").isDirectory());
		assertTrue(new File(remoteBase, "lib2").isDirectory());
		
		zan.uninstall("lib1");
		assertFalse(new File(localBase, "lib1").isDirectory());
		assertTrue(new File(localBase, "lib2").isDirectory());
		assertFalse(new File(remoteBase, "lib1").isDirectory());
		assertTrue(new File(remoteBase, "lib2").isDirectory());
	}
	
	@Test
	public void testList() throws ZANException{
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN("file://"+zanrepoDir.getAbsolutePath(), localBase, remoteBase, dfs);
		zan.update();
		
		List<Info> infos = zan.list();
		assertEquals(2, infos.size());
	}
}
