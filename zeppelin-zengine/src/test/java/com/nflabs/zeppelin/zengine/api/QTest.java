package com.nflabs.zeppelin.zengine.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.service.HiveServerException;
import org.apache.thrift.TException;

import com.jointhegrid.hive_test.HiveTestBase;
import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.util.UtilsForTests;
import com.nflabs.zeppelin.zengine.ParamInfo;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

public class QTest extends HiveTestService {

    private File tmpDir;
    private Zengine z;
    private ZeppelinDriver drv; 
    
    public void setUp() throws Exception {
        super.setUp();
        tmpDir = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());       
        tmpDir.mkdir();
        
        UtilsForTests.delete(new File("/tmp/warehouse"));
        UtilsForTests.delete(new File(ROOT_DIR.getName()));
        System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpDir.toURI().toString());

        //Dependencies: ZeppelinDriver + ZeppelinConfiguration + fs + RubyExecutionEngine
        z = new Zengine();
        z.configure();
        
        drv = UtilsForTests.createHiveTestDriver(z.getConf(), client);
    }

    public void tearDown() throws Exception {
        UtilsForTests.delete(tmpDir);        
        super.tearDown();
        
        UtilsForTests.delete(new File("/tmp/warehouse"));
        UtilsForTests.delete(new File(ROOT_DIR.getName()));
    }
    
    public QTest() throws IOException {
        super();
    }
    
    public void testLoadDataFromFileToNewTableQuery() throws HiveServerException, TException, ZException, IOException{
        //given Hive instance in local-mode
        //      ZeppelinConnection to it through ZeppelinDriver set-upped in Z
        //      test data from file
        Path p = genTestDataFile();

        //when
        new Q("drop table if exists test", z, drv).execute().result().write(System.out);
        new Q("create table test(a INT)", z, drv).execute().result().write(System.out);
        new Q("load data local inpath '" + p.toString() + "' into table test", z, drv).execute().result()
                .write(System.out);

        //then
        assertEquals(new Long(2), new Q("select count(*) from test", z, drv).execute().result().getRows().get(0)[0]);
    }
    
    private Path genTestDataFile() throws IOException {
        Path p = new Path(HiveTestBase.ROOT_DIR, "afile");
        FSDataOutputStream o = this.getFileSystem().create(p);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(o));
        bw.write("1\n");
        bw.write("2\n");
        bw.close();
        return p;
    }

    public void testName() throws HiveServerException, TException, ZException, IOException{
        Path p = genTestDataFile();

        new Q("drop view if exists test2", z, drv).execute().result().write(System.out);
        new Q("drop table if exists test", z, drv).execute().result().write(System.out);
        new Q("create table test(a INT)", z, drv).execute().result().write(System.out);
        new Q("load data local inpath '" + p.toString() + "' into table test", z, drv).execute().result()
                .write(System.out);
        Z q = new Q("select count(*) from test", z, drv).withName("test2").execute();
        assertEquals(new Long(2), new Q("select count(*) from test", z, drv).execute().result().getRows().get(0)[0]);
        
        new Q("select * from test2", z, drv).execute().result().write(System.out);
        assertEquals(new Long(1), new Q("select count(*) from test2", z, drv).execute().result().getRows().get(0)[0]);
        
        q.release();
        assertEquals(new Long(1), new Q("select count(*) from test2", z, drv).execute().result().getRows().get(0)[0]);
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
