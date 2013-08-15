package org.nflabs.zeppelin.zql;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.nflabs.zeppelin.job.Job;


import junit.framework.TestCase;

public class ZqlTest extends TestCase {

	private File tmpDir;


	protected void setUp() throws Exception {
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinAppTest_"+System.currentTimeMillis());
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

	
	public void testHiveInvalidApp(){
		new File(tmpDir.getAbsolutePath()+"/testapp1").mkdir();
		assertNull(App.load(new File(tmpDir.getAbsolutePath()+"/testapp1")));
	}
	
	
	public void testHiveAppWithHQL() throws IOException{
		new File(tmpDir.getAbsolutePath()+"/testapp2").mkdir();
		FileUtils.writeStringToFile(new File(tmpDir.getAbsolutePath()+"/testapp2/hql"), "hql template ${hql} option ${opt1} ${opt2=10} ${opt3=100}");
		App app = App.load(new File(tmpDir.getAbsolutePath()+"/testapp2"));
		assertNotNull(app);
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("opt1", "optv1");
		params.put("opt2", "0");
		assertEquals("hql template select * from tbl option optv1 0 100", app.getQuery(params, "select * from tbl"));
	}
	
	public void testHiveAppWithHQLERB() throws IOException{
		new File(tmpDir.getAbsolutePath()+"/testapp3").mkdir();
		FileUtils.writeStringToFile(new File(tmpDir.getAbsolutePath()+"/testapp3/hql.erb"), "erb template <%= $hql %> option <%= $opt1 %>");
		App app = App.load(new File(tmpDir.getAbsolutePath()+"/testapp3"));
		assertNotNull(app);
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("opt1", "optv1");
		assertEquals("erb template select * from tbl option optv1", app.getQuery(params, "select * from tbl"));
	}
	
	public void testVariablePersistencyERB() throws IOException{
		new File(tmpDir.getAbsolutePath()+"/testapp3").mkdir();
		FileUtils.writeStringToFile(new File(tmpDir.getAbsolutePath()+"/testapp3/hql.erb"), "erb template <%= $hql %> option <%= $opt1 %>");
		App app = App.load(new File(tmpDir.getAbsolutePath()+"/testapp3"));
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("opt1", "optv1");		
		assertEquals("erb template select * from tbl option optv1", app.getQuery(params, "select * from tbl"));
		
		FileUtils.writeStringToFile(new File(tmpDir.getAbsolutePath()+"/testapp3/hql.erb"), "erb template <%= $hql %> option <%= $opt1 %>");
		app = App.load(new File(tmpDir.getAbsolutePath()+"/testapp3"));
		params = new HashMap<String, String>();		
		assertEquals("erb template select * from tbl option ", app.getQuery(params, "select * from tbl"));
	}
	
	public void testZql() throws IOException{
		Zql zql = new Zql("select * from tbl1", tmpDir);
		List<Job> jobs = zql.compile();
		assertEquals(1, jobs.size());
		assertEquals("select * from tbl1", jobs.get(0).getScript());
	}
	
	public void testZqlWithPipe() throws IOException{
		Zql zql = new Zql("select * from tbl1 | select * from (${zql}) z", tmpDir);
		List<Job> jobs = zql.compile();
		assertEquals(1, jobs.size());
		assertEquals("select * from (select * from tbl1) z", jobs.get(0).getScript());
	}
	
	public void testMultipleZql() throws IOException{
		Zql zql = new Zql("select * from tbl1; hello world", tmpDir);
		List<Job> jobs = zql.compile();
		assertEquals(2, jobs.size());
		assertEquals("select * from tbl1", jobs.get(0).getScript());
		assertEquals("hello world", jobs.get(1).getScript());
	}

}
