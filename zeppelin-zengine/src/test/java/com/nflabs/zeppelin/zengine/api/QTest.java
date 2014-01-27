package com.nflabs.zeppelin.zengine.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.mock.MockDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.util.UtilsForTests;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

public class QTest extends TestCase {

    private File tmpDir;
    private Zengine z;
    private MockDriver drv; 
    
    public void setUp() throws Exception {
        super.setUp();
        tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());       
        tmpDir.mkdir();
        
        UtilsForTests.delete(new File("/tmp/warehouse"));
        System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());

        //Dependencies: ZeppelinDriver + ZeppelinConfiguration + fs + RubyExecutionEngine
		z = UtilsForTests.createZengine();
		drv = (MockDriver) z.getDriverFactory().createDriver("test");
    }

    public void tearDown() throws Exception {
        UtilsForTests.delete(tmpDir);        
        super.tearDown();
        
        UtilsForTests.delete(new File("/tmp/warehouse"));
    }
    
    public QTest() throws IOException {
        super();
    }
    

    public void testName() throws ZException, IOException, ResultDataException{
    	drv.queries.put("select count(*) from test", new Result(0, new String[]{"2"}));
        Z q = new Q("select count(*) from test", z, drv).withName("test2").execute();
        assertTrue(drv.views.containsKey("test2"));
        q.release();
        assertTrue(drv.views.containsKey("test2"));
    }
    
    public void testExtractParam() throws ZException {
        Z q = new Q("select <%=z.param('fieldname', 'hello')%> from here", z, drv).dryRun();
        Map<String, ParamInfo> infos = q.getParamInfos();
        assertEquals(1, infos.size());
        assertEquals("hello", infos.get("fieldname").getDefaultValue());
    }
    
    public void testRunQuery() throws ZException {
        //given
        //  zengine.set(mock(Hive) with table 2 rows in table "test")
        //when
        //  new Q("select * ..", zengine).execute()
        // OR
        //  zengine.new Q("select * ..", ).execute()
        // OR
        //  zengine.createQuery("select * ..")
        //then
        //  assertThat(result has 2 rows) 
        
        
        //given
        //  Z configured with ZeppelinDriver.getConnection() returns mock(ZeppelinConnection)
        //
        //  ZeppelinConnection connection = mock(ZeppelinConnection)
        //  ZeppelinDriver driver = mock(ZeppelinDriver);
        //  on.(driver.getConnection()).returns(connection)
        //
        //  Z.setDriver(driver)
        
        //when
        Result r = new Q("select count(*) from test", z, drv).execute().result();
        
        //then
        //assertThat(r.getRows().get(0)[0], is());
    }
}
