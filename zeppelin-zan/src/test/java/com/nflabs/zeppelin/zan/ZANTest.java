package com.nflabs.zeppelin.zan;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZANTest {

	private File tmpDir;
	private FileSystem dfs;

	@Before
	public void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZANTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();		
		dfs = FileSystem.get(new org.apache.hadoop.conf.Configuration());
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
		ZAN zan = new ZAN(localBase, remoteBase, dfs);

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
	public void testCRUD() throws GitAPIException, IOException, ZANException{
		// create remote zan repo
		File testzanrepo = new File(tmpDir.getAbsolutePath()+"/local/zanrepo/libname");

		FileRepositoryBuilder builder = new FileRepositoryBuilder();		
		FileRepository repo = builder.setGitDir(new File(testzanrepo, ".git"))
									 .build();		
		repo.create();
		repo.close();
		
		System.out.println(testzanrepo.toString());
		
		// commit some files to remore repo
		Git git = Git.open(testzanrepo);
		stringToFile(testzanrepo.getAbsolutePath()+"/file", "Hello world");
		git.add().addFilepattern("file").call();
		RevCommit rc = git.commit().setMessage("initial commit").call();
		git.push();
		
		// install
		String localBase = tmpDir.getAbsolutePath()+"/local";
		String remoteBase = tmpDir.getAbsolutePath()+"/remote";
		ZAN zan = new ZAN(localBase, remoteBase, dfs);
		zan.install("libname", "file://"+tmpDir.getAbsolutePath()+"/local/zanrepo/libname", "master", rc.getId().getName(), null);
		
		// check if file is installed
		assertTrue(new File(localBase+"/libname/file").isFile());
		// check if file is synced
		assertTrue(new File(remoteBase+"/libname/file").isFile());
		

		
		// Update library
		git = Git.open(testzanrepo);
		stringToFile(testzanrepo.getAbsolutePath()+"/file1", "new file");
		git.add().addFilepattern("file1").call();
		rc = git.commit().setMessage("add file").call();
		git.push();
		
		zan.upgrade("libname", "master", rc.getId().getName(), null);
		// check if file is installed
		assertTrue(new File(localBase+"/libname/file1").isFile());
		// check if file is synced
		assertTrue(new File(remoteBase+"/libname/file1").isFile());

		
		// Delete library
		zan.delete("libname");
		
		assertFalse(new File(localBase+"/libname").exists());		
		// check if file is synced
		assertFalse(new File(remoteBase+"/libname").exists());

		
	}
	
}
