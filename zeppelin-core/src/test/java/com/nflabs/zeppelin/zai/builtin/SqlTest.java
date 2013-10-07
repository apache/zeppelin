package com.nflabs.zeppelin.zai.builtin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.zai.Input;
import com.nflabs.zeppelin.zai.Output;
import com.nflabs.zeppelin.zai.Param;
import com.nflabs.zeppelin.zai.ParamSpecException;
import com.nflabs.zeppelin.zai.Resource;
import com.nflabs.zeppelin.zdd.ColumnDesc;
import com.nflabs.zeppelin.zdd.DataTypes;
import com.nflabs.zeppelin.zdd.Schema;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.User;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

import junit.framework.TestCase;

public class SqlTest extends TestCase {


	private File tmpPath;

	protected void setUp() throws Exception {		
		super.setUp();
		System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
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
	
	public void testFromText() throws IOException, ZeppelinRuntimeException, ParamSpecException{
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
		
		
		ZDD zdd = ZDD.createFromText(zr, "test", new Schema( 
				              new ColumnDesc[]{
				                     new ColumnDesc("key", DataTypes.STRING),
				                     new ColumnDesc("value", DataTypes.INT)
				              }), 
				              dir.getAbsolutePath(),
				              ',');

		zdd.evaluate();
		
		Sql sql = new Sql(zr);
		Output output = sql.run(new Input(new ZDD[]{zdd}, new Param[]{new Param<String>("query", "select count(*) from test")},null));
		ZDD[] outputData = output.getData();
		assertEquals(1, outputData.length);
		assertEquals(new Long(3), (((shark.api.Row[])(outputData[0].rdd().take(1)))[0].getLong(0)));

		zr.destroy();
		
	}

}
