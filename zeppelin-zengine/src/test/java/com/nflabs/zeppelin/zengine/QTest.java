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
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.util.TestUtil;

public class QTest extends HiveTestService{

	private File tmpDir;
	private File dataDir;


	public void setUp() throws Exception {
		super.setUp();
		tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmpDir.mkdir();
		dataDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis()+"/data");
		dataDir.mkdir();

		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());
		System.setProperty(ConfVars.HIVE_CONNECTION_URI.getVarName(), "jdbc:hive://localhost:10000/default");
		Z.configure(client);
		
	}

	public void tearDown() throws Exception {
		TestUtil.delete(tmpDir);
		super.tearDown();
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
		new Q("select count(*) from test").execute().result().write(System.out);
	}
}
