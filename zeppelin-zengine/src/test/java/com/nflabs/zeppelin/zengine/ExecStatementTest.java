package com.nflabs.zeppelin.zengine;

import java.io.IOException;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver;
import com.nflabs.zeppelin.result.Result;

public class ExecStatementTest extends HiveTestService {

	public ExecStatementTest() throws IOException {
		super();
	}

	public void setUp() throws Exception {
		super.setUp();
		Z.configure();
		HiveZeppelinDriver driver = new HiveZeppelinDriver(Z.conf());
		driver.setClient(client);
		Z.setDriver(driver);
	}

	public void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testExec() throws ZException{
		ExecStatement e = new ExecStatement("!echo \"hello world\"");
		Result result = e.execute().result();
		assertEquals("hello world", result.getRows().get(0)[0]);
	}
}
