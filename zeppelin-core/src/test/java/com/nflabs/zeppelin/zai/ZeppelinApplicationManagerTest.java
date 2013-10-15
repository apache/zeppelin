package com.nflabs.zeppelin.zai;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import junit.framework.TestCase;

public class ZeppelinApplicationManagerTest extends TestCase {

	private ZeppelinApplicationManager zam;


	protected void setUp() throws Exception {
		this.zam = new ZeppelinApplicationManager(null);
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testListApplication() throws InstantiationException, IllegalAccessException, SecurityException, IllegalArgumentException, NoSuchMethodException, InvocationTargetException{
		List<ZeppelinApplication> apps = zam.listApplications();
		assertNotNull(apps);
	}
	
	public void testRecommandApplication(){
		
	}
	
	
}
