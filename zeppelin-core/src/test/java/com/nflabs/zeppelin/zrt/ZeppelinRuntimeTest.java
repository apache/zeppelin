package com.nflabs.zeppelin.zrt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.zai.ColumnSpec;
import com.nflabs.zeppelin.zdd.ColumnDesc;
import com.nflabs.zeppelin.zdd.DataTypes;
import com.nflabs.zeppelin.zdd.Schema;
import com.nflabs.zeppelin.zdd.ZDD;

import junit.framework.TestCase;

public class ZeppelinRuntimeTest extends TestCase {

	private File tmpPath;
	private ZeppelinConfiguration conf;
	private ZeppelinRuntime zr;

	protected void setUp() throws Exception {
		super.setUp();
		System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
		String tempDir = System.getProperty("java.io.tmpdir")+"/zeppelin_test_"+System.currentTimeMillis();
		tmpPath = new File(tempDir);
		tmpPath.mkdirs();
		deleteRecursive(new File("./metastore_db"));
		
		Thread.sleep(3*1000); // to prevent address already use
		this.conf = new ZeppelinConfiguration();
		this.zr = new ZeppelinRuntime(conf, new User("test"));
	}

	protected void tearDown() throws Exception {
		zr.destroy();
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
	
	public void testInit(){
		assertTrue(true);
	}
	
	
}
