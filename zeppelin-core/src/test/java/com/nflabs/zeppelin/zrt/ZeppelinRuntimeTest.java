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

	protected void setUp() throws Exception {
		super.setUp();
		String tempDir = System.getProperty("java.io.tmpdir")+"/zeppelin_test_"+System.currentTimeMillis();
		tmpPath = new File(tempDir);
		tmpPath.mkdirs();
		deleteRecursive(new File("./metastore_db"));
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
	
	
	
	public void testFromText() throws IOException, ZeppelinRuntimeException{
		// create data dir
		File dir = new File(tmpPath.getAbsolutePath()+"/data");
		dir.mkdir();
		
		// write some data
		File data = new File(tmpPath.getAbsolutePath()+"/data/a");
		FileOutputStream out = new FileOutputStream(data);
		out.write("a,1\n".getBytes());
		out.write("b,2\n".getBytes());
		out.write("c,3".getBytes());
		out.flush();
		out.close();
		
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		ZeppelinRuntime zr = new ZeppelinRuntime(conf, new User("test"));
		ZDD zdd = zr.fromText(new Schema("test", 
				              new ColumnDesc[]{
				                     new ColumnDesc("key", DataTypes.STRING),
				                     new ColumnDesc("value", DataTypes.INT)
				              }), 
				              dir.toURI(), 
				              ',');
		
		assertEquals(3, zdd.rdd().count());
		
	}
}
