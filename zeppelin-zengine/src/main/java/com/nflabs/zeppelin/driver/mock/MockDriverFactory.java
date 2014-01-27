package com.nflabs.zeppelin.driver.mock;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.driver.ZeppelinDriver;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;

public class MockDriverFactory extends ZeppelinDriverFactory {
	Map<String, URI> drivers = new HashMap<String, URI>();
	private ZeppelinConfiguration conf;
	
	private MockDriverFactory(String driverRootDir, URI[] uriList)
			throws ZeppelinDriverException {
		super(null, driverRootDir, uriList);
	}
	
	public MockDriverFactory(ZeppelinConfiguration conf){
		super(conf, null, null);
		try {
			drivers.put("test", new URI("test://test"));
			drivers.put("production", new URI("test://production"));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

	}
	
	public Collection<String> getAllConfigurationNames(){		
		return drivers.keySet();
	}
	
	public String getDefaultConfigurationName(){
		return "test";
	}
	
	public ZeppelinDriver createDriver(String name) throws ZeppelinDriverException{
		URI uri = drivers.get(name);
		if (uri==null) {
			throw new ZeppelinDriverException("Driver "+name+" not found");
		}
		
		return new MockDriver(conf, uri, new URLClassLoader(new URL[]{}));
	}
}
