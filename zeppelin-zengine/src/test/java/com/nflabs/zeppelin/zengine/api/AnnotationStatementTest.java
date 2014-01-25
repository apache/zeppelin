package com.nflabs.zeppelin.zengine.api;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.nflabs.zeppelin.zengine.api.AnnotationStatement.ANNOTATION;
import com.nflabs.zeppelin.zengine.api.AnnotationStatement.COMMAND;

public class AnnotationStatementTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAnnotation() throws ZException {
		Zengine z = new Zengine();
		AnnotationStatement a = new AnnotationStatement("@driver set hive", z, null);
		assertEquals(ANNOTATION.DRIVER, a.getAnnotation());
		assertEquals(COMMAND.SET, a.getCommand());
		assertEquals("hive", a.getArgument());		
	}
	
	@Test
	public void testAnnotationEmptyArg() throws ZException {
		Zengine z = new Zengine();
		AnnotationStatement a = new AnnotationStatement("@driver set", z, null);
		assertEquals(ANNOTATION.DRIVER, a.getAnnotation());
		assertEquals(COMMAND.SET, a.getCommand());
		assertEquals(null, a.getArgument());		
	}
	
	@Test
	public void testInvalidAnnotation() throws ZException {
		Zengine z = new Zengine();
		AnnotationStatement a;
		try {
			a = new AnnotationStatement("@worng set hive", z, null);
			fail();
		} catch (ZException e) {
			// expected
		}
	}

	@Test
	public void testInvalidCommand() throws ZException {
		Zengine z = new Zengine();
		AnnotationStatement a;
		try {
			a = new AnnotationStatement("@driver wrong hive", z, null);
			fail();
		} catch (ZException e) {
			// expected
		}
	}
}
