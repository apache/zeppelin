package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zengine.L;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;

import junit.framework.TestCase;

public class LTest extends TestCase {
	
	private File tmpDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdir();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		delete(tmpDir);
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
		out.write("select * from table limit <%= z.param('limit') %>\n".getBytes());
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
		System.setProperty(ConfVars.ZEPPELIN_LIBRARY_DIR.getVarName(), tmpDir.toURI().toString());
		Z.init();
		
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
		assertEquals("CREATE VIEW "+test.name()+" AS select * from table limit 3", test.getQuery());
		List<URI> res = test.getResources();
		assertEquals(1, res.size());
		assertEquals("file://"+tmpDir.getAbsolutePath()+"/test/test_data.log", res.get(0).toString());
	}

}
