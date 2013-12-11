package com.nflabs.zeppelin.zengine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver;
import com.nflabs.zeppelin.util.TestUtil;

public class ZQLTest extends HiveTestService {
	public ZQLTest() throws IOException {
		super();
		// TODO Auto-generated constructor stub
	}

	private File tmpDir;
												
	public void setUp() throws Exception {
		super.setUp();
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();

		TestUtil.delete(new File("/tmp/warehouse"));
		TestUtil.delete(new File(ROOT_DIR.getName()));
		
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		Z.configure();
		HiveZeppelinDriver driver = new HiveZeppelinDriver(Z.getConf());
		driver.setClient(client);
		Z.setDriver(driver);		

		new File(tmpDir.getAbsolutePath()+"/test").mkdir();
		File erb = new File(tmpDir.getAbsolutePath()+"/test/zql.erb");
		FileOutputStream out = new FileOutputStream(erb);		
		out.write(("select * from (<%= z."+Q.INPUT_VAR_NAME+" %>) a limit <%= z.param('limit') %>\n").getBytes());
		out.close();
	
		new File(tmpDir.getAbsolutePath()+"/test1/web").mkdirs();
		File index = new File(tmpDir.getAbsolutePath()+"/test1/web/index.erb");
		out = new FileOutputStream(index);		
		out.write(("WEB\n").getBytes());
		out.close();
		
		// create resource
		FileOutputStream resource = new FileOutputStream(new File(tmpDir.getAbsolutePath()+"/test/test_data.log"));
		resource.write("".getBytes());
		resource.close();

	}

	public void tearDown() throws Exception {
		super.tearDown();
		delete(tmpDir);
		
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
	
	
	public void testPipe() throws ZException, ZQLException {
		ZQL zql = new ZQL();
		zql.append("select * from bank | select * from <%= z."+Q.INPUT_VAR_NAME+" %> limit 10");
		List<Z> z = zql.compile();
		
		assertEquals(1, z.size());
		assertEquals("select * from "+z.get(0).prev().name()+" limit 10", z.get(0).getQuery());
		z.get(0).release();
	}
	
	
	public void testSemicolon() throws ZException, ZQLException{
		ZQL zql = new ZQL();
		zql.append("create table if not exists bank(a INT); select * from bank | select * from <%= z."+Q.INPUT_VAR_NAME+" %> limit 10; show tables; ");
		List<Z> z = zql.compile();

		assertEquals(3, z.size());
		assertEquals("select * from "+z.get(1).prev().name()+" limit 10", z.get(1).getQuery());
		assertEquals("show tables", z.get(2).getQuery());
	}
	
	public void testRedirection() throws ZException, ZQLException{
		ZQL zql = new ZQL();
		zql.append("select * from bank limit 10 > summary");
		List<Z> z = zql.compile();
		
		assertEquals(1, z.size());
		assertEquals("select * from bank limit 10", z.get(0).getQuery());
	}

	public void testLstmtSimple() throws ZException, ZQLException{
		ZQL zql = new ZQL("test");
		List<Z> zList = zql.compile();
		assertEquals(1, zList.size());
		Z z = zList.get(0);
		assertEquals("select * from () a limit ", z.getQuery());
		z.release();
	}
	
	public void testLstmtParam() throws ZException, ZQLException{
		ZQL zql = new ZQL("test(limit=10)");
		Z z = zql.compile().get(0);
		assertEquals("select * from () a limit 10", z.getQuery());
	}
	
	public void testLstmtArg() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select * from test | test(limit=10)");
		
		List<Z> z = zql.compile();
		assertEquals(1, z.size());
		assertEquals("select * from ("+z.get(0).prev().name()+") a limit 10", z.get(0).getQuery());
	}
	
	public void testLstmtPipedArg() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select * from test | test1 | test1");
		
		List<Z> z = zql.compile();
		assertEquals(1, z.size());
		assertEquals(null, z.get(0).getQuery());
		assertEquals(null, z.get(0).prev().getQuery());
		assertEquals("select * from test", z.get(0).prev().prev().getQuery());
	}
	
	public void testMultilineQuery() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select\n*\nfrom\ntest");
		
		List<Z> z = zql.compile();
		assertEquals(1, z.size());
		assertEquals("select\n*\nfrom\ntest", z.get(0).getQuery());
	}


	public void testQueryCompilessOnAddJarStatement() throws ZException, ZQLException {
	    //on API level: why is ZException is not parent of ZQLException?
	    //              can API client do something meaningful catching each of them separately?
	    //              i.e recover from error BUT in different ways
	    //              why are they both are checked exceptions at all?

	    //given query without ' around path
        ZQL zql1 = new ZQL("ADD JAR /usr/lib/hive/lib/hive-contrib-0.11.0.1.3.2.0-111.jar;"+
                "CREATE  external TABLE test (id INT, name STRING) \nLOCATION \u0027hdfs://saturn01.nflabs.com/data-repo/CDN-LOGS/scslog\u0027\n;\n");

        ZQL zql = new ZQL("ADD JAR \u0027/usr/lib/hive/lib/hive-contrib-0.11.0.1.3.2.0-111.jar\u0027;\n\nCREATE external TABLE scslog (\n    hostname STRING,\n    level STRING,\n    servicename STRING,\n    time STRING,\n    responseTime STRING,\n    ip STRING,\n    status STRING,\n    size STRING,\n    method STRING,\n    url STRING,\n    username STRING,\n    cacheStatus STRING,\n    mime STRING,\n    requestHeader STRING,\n    responseHeader STRING)\nPARTITIONED BY(dt STRING, svc STRING)\nROW FORMAT SERDE \u0027org.apache.hadoop.hive.contrib.serde2.RegexSerDe\u0027\nWITH SERDEPROPERTIES (\n    \"input.regex\" \u003d \"([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) (-|\\\\[[^\\\\]]*\\\\]) (-|\\\\[[^\\\\]]*\\\\])\",\n    \"output.format.string\" \u003d \"%1$s %2$s %3$s %4$s %5$s %6$s %7$s %8$s %9$s %10$s %11$s %12$s %13$s %14$s %15$s\"\n)\nSTORED AS TEXTFILE\nLOCATION \u0027hdfs://saturn01.nflabs.com/data-repo/CDN-LOGS/scslog\u0027\n;\n\n\n\n\n");
        //when
        zql1.compile();
        zql.compile();
	}
	
	public void testExecStatmentQuery() throws ZException, ZQLException{
		ZQL zql = new ZQL("select * from test;!echo -n 'hello world';!echo ls");
		List<Z> z = zql.compile();
		assertEquals(3, z.size());
		assertEquals("select * from test", z.get(0).getQuery());
		assertEquals("!echo -n 'hello world';", z.get(1).getQuery());
		assertEquals("!echo ls", z.get(2).getQuery());
		assertTrue(z.get(1) instanceof ShellExecStatement); 
		assertTrue(z.get(2) instanceof ShellExecStatement); 
	}
}
