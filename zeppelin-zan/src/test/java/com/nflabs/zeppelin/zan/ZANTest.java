package com.nflabs.zeppelin.zan;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
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
	

}
