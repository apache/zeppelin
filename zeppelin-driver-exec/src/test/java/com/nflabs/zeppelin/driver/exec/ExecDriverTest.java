package com.nflabs.zeppelin.driver.exec;

import static org.junit.Assert.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ExecDriverTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testExec() throws URISyntaxException {
		ExecDriver driver = new ExecDriver(ZeppelinConfiguration.create(), new URI("jdbc:hive2://"), new URLClassLoader(new URL[]{}, Thread.currentThread().getContextClassLoader()));

	}

	/*
	 * 
    public void testExec() throws ZException, ZeppelinDriverException {
        //inheritance ExecStatment <- Q <- Z
        // z can .execute() itself through ZeppelinDriver (it manages actual connections and deligates to them)
    
        //given Hive instance in local-mode
        //      ZeppelinDriver (with ZeppelinConnection underneath)
        ShellExecStatement e = new ShellExecStatement("!echo \"hello world\"", z, drv);
        
        //when .execute()
        Result result = e.execute().result();
        
        //then
        assertEquals("hello world", result.getRows().get(0)[0]);
    }
	 */
}
