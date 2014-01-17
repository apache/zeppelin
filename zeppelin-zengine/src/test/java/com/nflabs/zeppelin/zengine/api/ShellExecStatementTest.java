package com.nflabs.zeppelin.zengine.api;

import java.io.IOException;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

public class ShellExecStatementTest extends HiveTestService {
    HiveZeppelinDriver driver;
    private Zengine z;
    
    public ShellExecStatementTest() throws IOException {
        super();
    }

    public void setUp() throws Exception {
        super.setUp();

        z = new Zengine();
        z.configure();
        
        // Configuration => Driver => Connection
        driver = new HiveZeppelinDriver(z.getConf());
        driver.setClient(client);
        z.setDriver(driver);
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testExec() throws ZException, ZeppelinDriverException {
        //inheritance ExecStatment <- Q <- Z
        // z can .execute() itself through ZeppelinDriver (it manages actual connections and deligates to them)
    
        //given Hive instance in local-mode
        //      ZeppelinDriver (with ZeppelinConnection underneath)
        ShellExecStatement e = new ShellExecStatement("!echo \"hello world\"", z);
        
        //when .execute()
        Result result = e.execute().result();
        
        //then
        assertEquals("hello world", result.getRows().get(0)[0]);
    }
}
