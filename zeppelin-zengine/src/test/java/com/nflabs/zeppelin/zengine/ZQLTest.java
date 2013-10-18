package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQL;
import com.nflabs.zeppelin.zengine.ZQLException;

import junit.framework.TestCase;

public class ZQLTest extends TestCase {
	private File tmpDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());
		tmpDir.mkdir();
		
		new File(tmpDir.getAbsolutePath()+"/test").mkdir();
		File erb = new File(tmpDir.getAbsolutePath()+"/test/test.erb");
		FileOutputStream out = new FileOutputStream(erb);		
		out.write("select * from (<%= z.arg %>) a limit <%= z.param('limit') %>\n".getBytes());
		out.close();
	
		// create resource
		FileOutputStream resource = new FileOutputStream(new File(tmpDir.getAbsolutePath()+"/test/test_data.log"));
		resource.write("".getBytes());
		resource.close();
		
		System.out.println(tmpDir.toURI().toString());
		System.setProperty(ConfVars.ZEPPELIN_LIBRARY_DIR.getVarName(), tmpDir.toURI().toString());
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
	
	
	public void testPipe() throws ZException, ZQLException {
		ZQL zql = new ZQL();
		zql.append("select * from bank | select * from (${arg}) limit 10");
		List<Z> z = zql.eval();
		
		assertEquals(1, z.size());
		assertEquals("select * from (select * from bank) limit 10", z.get(0).getQuery());
	}
	
	
	public void testSemicolon() throws ZException, ZQLException{
		ZQL zql = new ZQL();
		zql.append("select * from bank | select * from (${arg}) limit 10; show tables");
		List<Z> z = zql.eval();
		
		assertEquals(2, z.size());
		assertEquals("select * from (select * from bank) limit 10", z.get(0).getQuery());
		assertEquals("show tables", z.get(1).getQuery());
		
	}

	public void testLstmtSimple() throws ZException, ZQLException{
		ZQL zql = new ZQL("test");
		assertEquals("select * from () a limit ", zql.eval().get(0).getQuery());
	}
	
	public void testLstmtParam() throws ZException, ZQLException{
		ZQL zql = new ZQL("test(limit=10)");
		assertEquals("select * from () a limit 10", zql.eval().get(0).getQuery());
	}
	
	public void testLstmtArg() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select * from test | test(limit=10) select * from (${arg}) arg");
		
		List<Z> z = zql.eval();
		assertEquals(1, z.size());
		assertEquals("select * from (select * from (select * from test) arg) a limit 10", z.get(0).getQuery());
	}
	
}
