package com.nflabs.zeppelin.driver.hive11;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
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

        FileUtils.deleteDirectory(new File("/tmp/warehouse"));
        FileUtils.deleteDirectory(new File(ROOT_DIR.getName()));
	}

	@After
	public void tearDown() throws Exception {
        FileUtils.deleteDirectory(new File("/tmp/warehouse"));
        FileUtils.deleteDirectory(new File(ROOT_DIR.getName()));
	}

	@Test
	public void testQuery() throws URISyntaxException, IOException {
		HiveZeppelinDriver driver = new HiveZeppelinDriver(ZeppelinConfiguration.create(), new URI("jdbc:hive2://"), new URLClassLoader(new URL[]{}, Thread.currentThread().getContextClassLoader()));
		driver.setClient(client);
		
		// create table
		Result res = driver.query("create table if not exists test(a INT)");

		// show table
		res = driver.query("show tables");		
		assertEquals("test", res.getRows().get(0)[0]);

		// add some data
		FileOutputStream out = new FileOutputStream(new File("/tmp/warehouse/test/data"));
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
        bw.write("1\n");
	    bw.write("2\n");
	    bw.close();

	    // count
	    res = driver.query("select count(*) from test");
	    assertEquals(new Long(2), res.getRows().get(0)[0]);
	}

}
