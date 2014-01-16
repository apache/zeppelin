package com.nflabs.zeppelin.zengine.api;

import java.io.IOException;

import com.jointhegrid.hive_test.HiveTestService;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.hive.HiveZeppelinDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.api.ShellExecStatement;
import com.nflabs.zeppelin.zengine.api.Z;

public class ShellExecStatementTest extends HiveTestService {
    HiveZeppelinDriver driver;
    
    public ShellExecStatementTest() throws IOException {
        super();
    }

    public void setUp() throws Exception {
        super.setUp();

        Z.configure();
        
        // Configuration => Driver => Connection
        driver = new HiveZeppelinDriver(Z.getConf());
        driver.setClient(client);
        Z.setDriver(driver);
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testExec() throws ZException, ZeppelinDriverException {
        //inheritance ExecStatment <- Q <- Z
        // z can .execute() itself through ZeppelinConnection
        // ZeppelinConnection can be acquired from ZeppelinDirver
    
        //given Hive instance in local-mode
        //      ZeppelinConnection to it through ZeppelinDriver
        ShellExecStatement e = new ShellExecStatement("!echo \"hello world\"");
        
        //when .execute()
        Result result = e.execute(driver.getConnection()).result();
        
        //then
        assertEquals("hello world", result.getRows().get(0)[0]);
    }
}
