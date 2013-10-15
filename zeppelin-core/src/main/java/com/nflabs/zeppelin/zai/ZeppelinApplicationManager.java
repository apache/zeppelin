package com.nflabs.zeppelin.zai;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.reflections.Reflections;

import com.nflabs.zeppelin.zrt.ZeppelinRuntime;

public class ZeppelinApplicationManager {
	private ZeppelinRuntime runtime;

	public ZeppelinApplicationManager(ZeppelinRuntime runtime){
		this.runtime = runtime;
	}
	
	public List<ZeppelinApplication> listApplications() throws InstantiationException, IllegalAccessException, SecurityException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException{
		List<ZeppelinApplication> apps = new LinkedList<ZeppelinApplication>();
		
		// load builtin apps
		Reflections reflections = new Reflections("");
		Set<Class<? extends ZeppelinApplication>> classes = reflections.getSubTypesOf(ZeppelinApplication.class);		
		Iterator<Class<? extends ZeppelinApplication>> it = classes.iterator();
		while(it.hasNext()){
			Class<? extends ZeppelinApplication> cls = it.next();

			if(cls.getName().equals(MetaApplication.class.getName())){
				continue;
			}
			

			Constructor<? extends ZeppelinApplication> constructor = cls.getConstructor(ZeppelinRuntime.class);
			ZeppelinApplication app = constructor.newInstance(runtime);
			apps.add(app);			
		}
		
		return apps;		
	}
	
	

}
