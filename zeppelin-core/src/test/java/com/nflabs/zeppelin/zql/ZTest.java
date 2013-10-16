package com.nflabs.zeppelin.zql;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

import junit.framework.TestCase;

public class ZTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testPipeGetQuery(){
		assertEquals("create view vv as select * from (select * from bank) q limit 10", new Q("select * from bank")
																	  .pipe(new Q("select * from (${q}) q limit 10"))
																	  .pipe(new Q("create view vv as"))
																	  .getQuery()
		);
	}
	
	public void testShowTables() throws ClassNotFoundException, SQLException{
		ZeppelinConfiguration conf = new ZeppelinConfiguration();
		Z.init(conf);
		
		new Q("show tables").execute();
		
	}
}
