package org.apache.zeppelin.driver;

import java.util.HashMap;
import java.util.Map;

public class DriverManager {
	Map<String, Driver> drivers = new HashMap<String, Driver>();
	
	public DriverManager(){
		
	}
	
	
	public Driver get(String driverClass) throws ClassNotFoundException, InstantiationException, IllegalAccessException{
		Driver d = drivers.get(driverClass);
		if(d!=null) return d;
		
		synchronized(drivers){
			d = drivers.get(driverClass);
			if(d!=null) return d;
			
			d = createDriver(driverClass);
			drivers.put(driverClass, d);
			return d;
		}
	}
	
	
	private Driver createDriver(String driverClass) throws ClassNotFoundException, InstantiationException, IllegalAccessException{
		Class<?> cls = Class.forName(driverClass);
		Driver d = (Driver) cls.newInstance();
		d.init();
		return d;
	}
	
	
	public void remove(String driverClass){
		synchronized(drivers){
			Driver d = drivers.get(driverClass);
			if(d!=null){
				d.terminate();
				drivers.remove(driverClass);
			}
		}			
	}
	
	public void removeAll(){
		synchronized(drivers){
			for(String cls : drivers.keySet()){
				remove(cls);
			}
		}
	}
}
