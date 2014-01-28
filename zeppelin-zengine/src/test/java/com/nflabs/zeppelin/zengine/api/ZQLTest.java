package com.nflabs.zeppelin.zengine.api;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;

import com.google.common.collect.ImmutableMap;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;
import com.nflabs.zeppelin.driver.mock.MockDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.util.UtilsForTests;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQLException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.sun.script.jruby.JRubyScriptEngineFactory;

public class ZQLTest extends TestCase {

    public ZQLTest() throws IOException {
		super();
		// TODO Auto-generated constructor stub
	}

	private static File tmpDir;
	private Zengine z;
						
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();
		String tmpDirPath = tmpDir.getAbsolutePath();

		UtilsForTests.delete(new File("/tmp/warehouse"));
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());

		String q1 = "select * from (<%= z."+Q.INPUT_VAR_NAME+" %>) a limit <%= z.param('limit') %>\n";      
		new File(tmpDirPath + "/test").mkdir();
		UtilsForTests.createFileWithContent(tmpDirPath + "/test/zql.erb", q1);
		UtilsForTests.createFileWithContent(tmpDirPath + "/test/test_data.log", "");
		new File(tmpDirPath + "/test1").mkdir();
		new File(tmpDirPath + "/test1/web").mkdirs();
		UtilsForTests.createFileWithContent(tmpDirPath + "/test1/web/index.erb", "WEB\n");

		
		//Dependencies: collection of ZeppelinDrivers + ZeppelinConfiguration + fs + RubyExecutionEngine
		z = UtilsForTests.createZengine();
	}

    @After
	public void tearDown() throws Exception {
		super.tearDown();
		UtilsForTests.delete(tmpDir);
		UtilsForTests.delete(new File("/tmp/warehouse"));
	}	
	
	public void testPipe() throws ZException, ZQLException {
		ZQL zql = new ZQL(z);
		zql.append("select * from bank | select * from <%= z."+Q.INPUT_VAR_NAME+" %> limit 10");
		ZPlan plan = zql.compile();
		
		assertEquals(1, plan.size());
		assertEquals("select * from "+plan.get(0).prev().name()+" limit 10", plan.get(0).getQuery());
		plan.get(0).release();
	}
	
	
	public void testSemicolon() throws ZException, ZQLException{
		ZQL zql = new ZQL(z);
		zql.append("create table if not exists bank(a INT); select * from bank | select * from <%= z."+Q.INPUT_VAR_NAME+" %> limit 10; show tables; ");
		List<Z> plan = zql.compile();

		assertEquals(3, plan.size());
		assertEquals("select * from "+plan.get(1).prev().name()+" limit 10", plan.get(1).getQuery());
		assertEquals("show tables", plan.get(2).getQuery());
		
		for (Z query : plan) {
		    assertNotNull(query.getDriver());
		}
	}
	
	public void testRedirection() throws ZException, ZQLException{
		ZQL zql = new ZQL(z);
		zql.append("select * from bank limit 10 > summary");
		List<Z> plan = zql.compile();
		
		assertEquals(1, plan.size());
		assertEquals("select * from bank limit 10", plan.get(0).getQuery());
	}

	public void testLstmtSimple() throws ZException, ZQLException{
		ZQL zql = new ZQL("test", z);
		List<Z> zList = zql.compile();
		assertEquals(1, zList.size());
		Z q = zList.get(0);
		assertEquals("select * from () a limit ", q.getQuery());
		q.release();
	}
	
	public void testLstmtParam() throws ZException, ZQLException{
		ZQL zql = new ZQL("test(limit=10)", z);
		Z q = zql.compile().get(0);
		assertEquals("select * from () a limit 10", q.getQuery());
	}
	
	public void testLstmtArg() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select * from test | test(limit=10)", z);
		
		List<Z> q = zql.compile();
		assertEquals(1, q.size());
		assertEquals("select * from ("+q.get(0).prev().name()+") a limit 10", q.get(0).getQuery());
	}
	
	public void testLstmtPipedArg() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select * from test | test1 | test1", z);
		
		List<Z> q = zql.compile();
		assertEquals(1, q.size());
		assertEquals(null, q.get(0).getQuery());
		assertEquals(null, q.get(0).prev().getQuery());
		assertEquals("select * from test", q.get(0).prev().prev().getQuery());
	}
	
	public void testMultilineQuery() throws IOException, ZException, ZQLException{
		ZQL zql = new ZQL("select\n*\nfrom\ntest", z);
		
		List<Z> q = zql.compile();
		assertEquals(1, q.size());
		assertEquals("select\n*\nfrom\ntest", q.get(0).getQuery());
	}


	public void testQueryCompilessOnAddJarStatement() throws ZException, ZQLException {
	    //on API level: why is ZException is not parent of ZQLException?
	    //              can API client do something meaningful catching each of them separately?
	    //              i.e recover from error BUT in different ways
	    //              why are they both are checked exceptions at all?

	    //given query without ' around path
        ZQL zql1 = new ZQL("ADD JAR /usr/lib/hive/lib/hive-contrib-0.11.0.1.3.2.0-111.jar;"+
                "CREATE  external TABLE test (id INT, name STRING) \nLOCATION \u0027hdfs://saturn01.nflabs.com/data-repo/CDN-LOGS/scslog\u0027\n;\n", z);

        ZQL zql = new ZQL("ADD JAR \u0027/usr/lib/hive/lib/hive-contrib-0.11.0.1.3.2.0-111.jar\u0027;\n\nCREATE external TABLE scslog (\n    hostname STRING,\n    level STRING,\n    servicename STRING,\n    time STRING,\n    responseTime STRING,\n    ip STRING,\n    status STRING,\n    size STRING,\n    method STRING,\n    url STRING,\n    username STRING,\n    cacheStatus STRING,\n    mime STRING,\n    requestHeader STRING,\n    responseHeader STRING)\nPARTITIONED BY(dt STRING, svc STRING)\nROW FORMAT SERDE \u0027org.apache.hadoop.hive.contrib.serde2.RegexSerDe\u0027\nWITH SERDEPROPERTIES (\n    \"input.regex\" \u003d \"([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) (-|\\\\[[^\\\\]]*\\\\]) (-|\\\\[[^\\\\]]*\\\\])\",\n    \"output.format.string\" \u003d \"%1$s %2$s %3$s %4$s %5$s %6$s %7$s %8$s %9$s %10$s %11$s %12$s %13$s %14$s %15$s\"\n)\nSTORED AS TEXTFILE\nLOCATION \u0027hdfs://saturn01.nflabs.com/data-repo/CDN-LOGS/scslog\u0027\n;\n\n\n\n\n", z);
        //when
        zql1.compile();
        zql.compile();
	}
	
	public void testAnnotationStatmentQuery() throws ZException, ZQLException{
		ZQL zql = new ZQL("select * from test;@driver set production;!echo ls", z);
		List<Z> plan = zql.compile();
		assertEquals(3, plan.size());
		assertEquals("select * from test", plan.get(0).getQuery());
		assertEquals("@driver set production", plan.get(1).getQuery());
		assertEquals("!echo ls", plan.get(2).getQuery());
		assertTrue(plan.get(1) instanceof AnnotationStatement); 
		assertTrue(plan.get(2) instanceof Q); 
	}
	
	public void testPerformance() throws Exception{
		MockDriver.queries.put("select * from tbl", new Result(0, new String[]{"hello"}));
		new ZQL("select * from tbl", z).compile().execute();
		long start = System.currentTimeMillis();		
		int count=0;
		while(System.currentTimeMillis() - start < 2000){
			count++;
			new ZQL("select * from tbl", z).compile().execute();
		}
		long end = System.currentTimeMillis();
		long diff = end - start;
		System.out.println("ZQL performance " + (float)count*1000/diff+" run/sec");
	}
}
