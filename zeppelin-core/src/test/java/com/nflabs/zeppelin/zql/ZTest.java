package com.nflabs.zeppelin.zql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

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
		
		new Q("create table if not exists test(a INT, b STRING)").execute();
		List<ResultSet> results = new Q("show tables").execute();
		assertEquals(1, results.size());
		results.get(0).next();
		assertEquals("test", results.get(0).getString(1));
		
		results = new Q("drop table test; show tables").execute();
		assertEquals(2, results.size());
		assertFalse(results.get(1).next());
	}
}
