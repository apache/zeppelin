package com.nflabs.zeppelin.zengine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.util.TestUtil;
import com.nflabs.zeppelin.zengine.L;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;

import junit.framework.TestCase;

public class LTest extends HiveTestService {
	
	public LTest() throws IOException {
		super();
	}

	private File tmpDir;


	public void setUp() throws Exception {
		super.setUp();
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();

		TestUtil.delete(new File("/tmp/warehouse"));
		TestUtil.delete(new File(ROOT_DIR.getName()));
		
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		Z.configure(client);
		
	}

	public void tearDown() throws Exception {
		delete(tmpDir);
		super.tearDown();
		
		TestUtil.delete(new File("/tmp/warehouse"));
		TestUtil.delete(new File(ROOT_DIR.getName()));
	}
	
	private void delete(File file){
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

	
	public void testLoadFromDir() throws IOException, ZException{
		new File(tmpDir.getAbsolutePath()+"/test").mkdir();
		File erb = new File(tmpDir.getAbsolutePath()+"/test/test.erb");
		FileOutputStream out = new FileOutputStream(erb);		
		out.write(("CREATE VIEW <%= z."+Q.OUTPUT_VAR_NAME+" %> AS select * from table limit <%= z.param('limit') %>\n").getBytes());
		out.close();
		
		// create resource that will be ignored
		FileOutputStream outInvalid = new FileOutputStream(new File(tmpDir.getAbsolutePath()+"/test/no_resource"));
		outInvalid.write("".getBytes());
		outInvalid.close();
		
		// create resource
		FileOutputStream resource = new FileOutputStream(new File(tmpDir.getAbsolutePath()+"/test/test_data.log"));
		resource.write("".getBytes());
		resource.close();
		
		System.out.println(tmpDir.toURI().toString());
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		Z.configure();
		
		// load nonexisting L
		try{
			new L("abc");
			assertTrue(false);
		}catch(ZException e){
			assertTrue(true);
		}
		
		// load existing L
		L test = new L("test");
		test.withParam("limit", 3);
		test.withName("hello");
		assertEquals("CREATE VIEW "+test.name()+" AS select * from table limit 3", test.getQuery());
		List<URI> res = test.getResources();
		assertEquals(1, res.size());
		assertEquals("file://"+tmpDir.getAbsolutePath()+"/test/test_data.log", res.get(0).toString());
		test.release();
	}

	public void testWeb() throws Exception{
		new File(tmpDir.getAbsolutePath()+"/test/web").mkdirs();
		File erb = new File(tmpDir.getAbsolutePath()+"/test/test.erb");
		FileOutputStream out = new FileOutputStream(erb);
		out.write(("show tables").getBytes());
		out.close();

		erb = new File(tmpDir.getAbsolutePath()+"/test/web/index.erb");
		out = new FileOutputStream(erb);		
		out.write("HELLO HTML\n".getBytes());
		out.close();

		// load existing L
		Z test = new L("test");//.execute();
		InputStream ins = test.readWebResource("/");
		assertEquals("HELLO HTML", IOUtils.toString(ins, "utf8"));
	}
	
	public void testWebOnlyLibrary() throws IOException, ZException{
		new File(tmpDir.getAbsolutePath()+"/test/web").mkdirs();

		Path p = new Path(this.ROOT_DIR, "afile");

	    FSDataOutputStream o = this.getFileSystem().create(p);
	    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(o));
	    bw.write("5\n");
	    bw.write("2\n");
	    bw.close();

		new Q("drop table if exists test").execute().result().write(System.out);
		new Q("create table test(a INT)").execute().result().write(System.out);
		new Q("load data local inpath '" + p.toString() + "' into table test").execute().result().write(System.out);

		File erb = new File(tmpDir.getAbsolutePath()+"/test/web/index.erb");
		FileOutputStream out = new FileOutputStream(erb);		
		out.write("HELLO HTML <%= z.result.rows[0][0] %>\n".getBytes());
		out.close();
		
		Z z = new Q("select * from test").pipe(new L("test"));
		Result result = z.execute().result();
		assertEquals(5, result.getRows().get(0)[0]);
		
		InputStream ins = z.readWebResource("/");
		assertEquals("HELLO HTML 5", IOUtils.toString(ins, "utf8"));
	}
}
