package com.nflabs.zeppelin.zai;

import junit.framework.TestCase;

public class ParamSpecTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	public void testParamSpec() throws ParamSpecException{
		ParamSpec.IntegerParamSpec param = new ParamSpec.IntegerParamSpec("age");
		
		// set default
		param.withDefaultValue(12);
		assertEquals(new Integer(12), param.validate(null));
		
		// other value
		try{
			assertEquals(new Integer(10), param.validate(10));
			assertTrue(false);
		} catch(ParamSpecException e){			
		}
		
		// with option
		param.addOption(10);
		assertEquals(new Integer(10), param.validate(10));
		assertEquals(new Integer(12), param.validate(12));

		
		// allow any
		param.withAllowAny(true);
		assertEquals(new Integer(9), param.validate(9));
		assertEquals(new Integer(10), param.validate(10));
		assertEquals(new Integer(12), param.validate(12));

		
		// from range
		param.from(10);
		try{
			assertEquals(new Integer(9), param.validate(9));
			assertTrue(false);
		} catch(ParamSpecException e){}
		assertEquals(new Integer(10), param.validate(10));
		assertEquals(new Integer(12), param.validate(12));

		// to range
		param.to(11);
		try{
			assertEquals(new Integer(9), param.validate(9));
			assertTrue(false);
		} catch(ParamSpecException e){}
		assertEquals(new Integer(10), param.validate(10));
		assertEquals(new Integer(11), param.validate(11));
		assertEquals(new Integer(12), param.validate(12));
		try{
			assertEquals(new Integer(13), param.validate(13));
			assertTrue(false);
		} catch(ParamSpecException e){}		


	}
}
