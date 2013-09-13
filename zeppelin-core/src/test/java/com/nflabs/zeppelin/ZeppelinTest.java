package com.nflabs.zeppelin;

import java.io.File;
import java.io.IOException;

import com.nflabs.zeppelin.Zeppelin;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;


import junit.framework.TestCase;

public class ZeppelinTest extends TestCase {

	private File tmpPath;

	protected void setUp() throws Exception {
		super.setUp();
		String tempDir = System.getProperty("java.io.tmpdir")+"/zeppelin_test_"+System.currentTimeMillis();
		tmpPath = new File(tempDir);
		tmpPath.mkdirs();
	}

	protected void tearDown() throws Exception {
		deleteRecursive(tmpPath);
		super.tearDown();
	}
	
	private void deleteRecursive(File file){
		if(file.isDirectory()){
			for(File f : file.listFiles()){
				deleteRecursive(f);
			}
			file.delete();
		} else if(file.isFile()){
			file.delete();
		}
	}

	public void testZeppelin() throws InterruptedException, IOException{
		Zeppelin zp = new Zeppelin(new ZeppelinConfiguration());
		
	}
}
