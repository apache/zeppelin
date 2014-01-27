package com.nflabs.zeppelin.zengine.api;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.mock.MockDriver;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.util.UtilsForTests;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;

public class LTest extends TestCase {
    
    private File tmp;
	private String tmpDir;
    private String tmpUri;

    @Rule
    public ExpectedException thrown= ExpectedException.none();
    private Zengine z;
    private MockDriver drv;
    
    public LTest() throws IOException {
        super();
    }

    @Before
	public void setUp() throws Exception {
		super.setUp();
		tmp = new File(System.getProperty("java.io.tmpdir")+"/ZeppelinLTest_"+System.currentTimeMillis());		
		tmp.mkdir();
		tmpDir = tmp.getAbsolutePath();
		tmpUri = tmp.toURI().toString();
		
		System.setProperty(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO.getVarName(), tmpUri );
		z = UtilsForTests.createZengine();
		drv = (MockDriver) z.getDriverFactory().createDriver("test");
	}

    @After
	public void tearDown() throws Exception {
		UtilsForTests.delete(tmp);
		super.tearDown();		
	}
    

	@Test
	public void testLoadingNonExistentLibrary() throws IOException, ZException {
		generateTestLibraryIn(tmpDir);
		
		thrown.expect(ZException.class);
		// load nonexisting L
		try {
		    new L("abc", z, drv);
		} catch (ZException e) {
		    assertTrue(e.getMessage().contains("does not exist"));
		}
	}

	/**
	 * Generates the mock of Zeppelin Library in file system
	 * @param path of the library root
	 * @throws IOException
	 */
    private void generateTestLibraryIn(String path) throws IOException {
        File f = new File(path+"/test");
        if (!f.exists()) { f.mkdir(); }
        
        String zqlQuery = "CREATE VIEW <%= z." + Q.OUTPUT_VAR_NAME + " %> AS select * from table limit <%= z.param('limit') %>\n";
        
        UtilsForTests.createFileWithContent(path+"/test/zql.erb", zqlQuery);
        // create resource that will be ignored
        UtilsForTests.createFileWithContent(path+"/test/no_resource", "");
        // create resource
        UtilsForTests.createFileWithContent(path+"/test/test_data.log", "");
    }
	
	@Test
	public void testLoadingExistingLibrary() throws ZException, IOException {
	    generateTestLibraryIn(tmpDir);

        // load existing L
        L test = new L("test", z, drv);
        test.withParam("limit", 3);
        test.withName("hello");
        assertEquals("CREATE VIEW "+test.name()+" AS select * from table limit 3", test.getQuery());
        List<URI> res = test.getResources();
        assertEquals(1, res.size());
        assertEquals("file://"+tmpDir+"/test/test_data.log", res.get(0).toString());
        test.release();
	}

	public void testWeb() throws Exception{
		new File(tmpDir+"/test/web").mkdirs();

		UtilsForTests.createFileWithContent(tmpDir+"/test/zql.erb", "show tables");
		UtilsForTests.createFileWithContent(tmpDir+"/test/web/index.erb", "HELLO HTML\n");

		// load existing L
		Z test = new L("test", z, drv);//.execute();
		InputStream ins = test.readWebResource("/");
		assertEquals("HELLO HTML", IOUtils.toString(ins, "utf8"));
	}
	
	public void testWebOnlyLibrary() throws IOException, ZException, ResultDataException{
		new File(tmpDir+"/test/web").mkdirs();
        UtilsForTests.createFileWithContent(tmpDir+"/test/web/index.erb", "HELLO HTML <%= z.result.rows[0][0] %>\n");

		drv.queries.put("select * from test", new Result(0, new String[]{"5"}));
		
		Z q = new Q("select * from test", z, drv).pipe(new L("test", z, drv));
		Result result = q.execute().result();
		assertEquals("5", result.getRows().get(0)[0]);
		
		InputStream ins = q.readWebResource("/");
		assertEquals("HELLO HTML 5", IOUtils.toString(ins, "utf8"));
	}
	
	public void testWebOnlyLibraryPipe() throws IOException, ZException, ResultDataException{
		drv.queries.put("select * from test", new Result(0, new String[]{"5"}));

		new File(tmpDir+"/test/web").mkdirs();

		File erb = new File(tmpDir+"/test/web/index.erb");
		FileOutputStream out = new FileOutputStream(erb);		
		out.write("HELLO HTML <%= z.result.rows[0][0] %>\n".getBytes());
		out.close();
		
		Z q = new Q("select * from test", z, drv).pipe(new L("test", z, drv)).pipe(new L("test", z, drv));
		Result result = q.execute().result();
		assertEquals("5", result.getRows().get(0)[0]);
	}

}
