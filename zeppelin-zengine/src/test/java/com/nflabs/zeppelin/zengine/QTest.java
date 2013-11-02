package com.nflabs.zeppelin.zengine;


import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.service.HiveInterface;
import org.apache.hadoop.hive.service.HiveServerException;
import org.apache.thrift.TException;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.util.TestUtil;

public class QTest extends HiveTestService{

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
		TestUtil.delete(tmpDir);		
		super.tearDown();
		
		TestUtil.delete(new File("/tmp/warehouse"));
		TestUtil.delete(new File(ROOT_DIR.getName()));
	}
	
	public QTest() throws IOException {
		super();
	}

	
	public void testBasicQuery() throws HiveServerException, TException, ZException, IOException{
		Path p = new Path(this.ROOT_DIR, "afile");

	    FSDataOutputStream o = this.getFileSystem().create(p);
	    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(o));
	    bw.write("1\n");
	    bw.write("2\n");
	    bw.close();

		new Q("drop table if exists test").execute().result().write(System.out);
		new Q("create table test(a INT)").execute().result().write(System.out);
		new Q("load data local inpath '" + p.toString() + "' into table test").execute().result().write(System.out);
		assertEquals(new Long(2), new Q("select count(*) from test").execute().result().getRows().get(0)[0]);
	}
	
	
	public void testName() throws HiveServerException, TException, ZException, IOException{
		Path p = new Path(this.ROOT_DIR, "afile");

	    FSDataOutputStream o = this.getFileSystem().create(p);
	    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(o));
	    bw.write("1\n");
	    bw.write("2\n");
	    bw.close();

	    new Q("drop view if exists test2").execute().result().write(System.out);
	    new Q("drop table if exists test").execute().result().write(System.out);
		new Q("create table test(a INT)").execute().result().write(System.out);
		new Q("load data local inpath '" + p.toString() + "' into table test").execute().result().write(System.out);
		Z z = new Q("select count(*) from test").withName("test2").execute();
		assertEquals(new Long(2), new Q("select count(*) from test").execute().result().getRows().get(0)[0]);
		new Q("select * from test2").execute().result().write(System.out);
		assertEquals(new Long(1), new Q("select count(*) from test2").execute().result().getRows().get(0)[0]);
		z.release();
		assertEquals(new Long(1), new Q("select count(*) from test2").execute().result().getRows().get(0)[0]);
	}
}
