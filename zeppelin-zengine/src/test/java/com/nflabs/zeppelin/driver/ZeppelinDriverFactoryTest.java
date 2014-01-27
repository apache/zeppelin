package com.nflabs.zeppelin.driver;

import static org.junit.Assert.*;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZeppelinDriverFactoryTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws URISyntaxException {
		URI uri = new URI("production:hive:com.nflabs.zeppelin.hive.driver.HiveDriver://localhost:10000/default");
		assertEquals("production", uri.getScheme());
		assertEquals("hive", new URI(uri.getSchemeSpecificPart()).getScheme());
		assertEquals("localhost", new URI(new URI(uri.getSchemeSpecificPart()).getSchemeSpecificPart()).getHost());
	}

}
