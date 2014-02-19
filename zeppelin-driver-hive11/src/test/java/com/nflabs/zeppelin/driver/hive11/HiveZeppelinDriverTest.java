package com.nflabs.zeppelin.driver.hive11;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.result.Result;

public class HiveZeppelinDriverTest extends HiveTestService {

	private File tmpDir;

	public HiveZeppelinDriverTest() throws IOException {
		super();
	}
	  
	@Before
	public void setUp() throws Exception {
		super.setUp();
        tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());                
        tmpDir.mkdir();
        System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), ROOT_DIR.getName());
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(new File(ROOT_DIR.getName()));
		FileUtils.deleteDirectory(tmpDir);
	}

	@Test
	public void testQuery() throws URISyntaxException, IOException {
		HiveZeppelinDriver driver = new HiveZeppelinDriver();
		driver.setConf(ZeppelinConfiguration.create());
		ZeppelinConnection conn = driver.getConnection("hive2://");
		driver.setClient(client);

		// create table
		Result res = conn.query("create database if not exists default");
		res = conn.query("create table if not exists test(a INT)");

		// show table
		res = conn.query("show tables");
		assertEquals("test", res.getRows().get(0)[0]);

		// add some data
		FileOutputStream out = new FileOutputStream(new File(ROOT_DIR.getName()
				+ "/warehouse/test/data"));
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
		for (int i = 1; i <= 200; i++) {
			bw.write(i + "\n");
		}
		bw.close();

		// count
		res = conn.query("select count(*) from test");
		assertEquals(new Long(200), res.getRows().get(0)[0]);

		// get
		res = conn.query("select * from test");
		assertEquals(200, res.getRows().size());

		// clear
		conn.query("drop table test");
	}
}
