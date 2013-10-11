package com.nflabs.zeppelin.zdd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.zrt.User;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

import junit.framework.TestCase;

public class ZDDTest extends TestCase {

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
		
		
		ZDD zdd = ZDD.createFromText(zr, "test", new Schema( 
				              new ColumnDesc[]{
				                     new ColumnDesc("key", DataTypes.STRING),
				                     new ColumnDesc("value", DataTypes.INT)
				              }), 
				              dir.getAbsolutePath(),
				              ',');

		assertEquals(3, zdd.tableRdd().count());
		
		assertEquals(dir.getAbsolutePath().toString(), zdd.getLocation());
		
		ZDD select = zr.fromTable(zdd.name());
		assertEquals("a", select.tableRdd().first().getString(0));
		
		assertEquals("file:"+dir.getAbsolutePath().toString(), select.getLocation());
		
		ZDD sum = zr.fromSql("test1", "select sum(value) from "+zdd.name());
		assertEquals(new Long(6), sum.tableRdd().first().getLong(0));
		
	}
	
	public void testFromRDD() throws IOException{
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
		
		ZDD zdd = ZDD.createFromText(zr, "test", new Schema( 
	              new ColumnDesc[]{
	                     new ColumnDesc("key", DataTypes.STRING),
	                     new ColumnDesc("value", DataTypes.INT)
	              }), 
	              dir.getAbsolutePath(),
	              ',');
	}

}
