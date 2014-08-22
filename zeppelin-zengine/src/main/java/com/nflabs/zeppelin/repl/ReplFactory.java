package com.nflabs.zeppelin.repl;


import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ReplFactory {
	Logger logger = LoggerFactory.getLogger(ReplFactory.class);
	
	private ZeppelinConfiguration conf;

	public ReplFactory(ZeppelinConfiguration conf){
		this.conf = conf;
	}
	
	
	public Repl createRepl(String className, Reader reader, Writer writer)  {
		logger.info("Create {} repl", className);
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
		URL [] urls = new URL[]{};
		URLClassLoader cl = new URLClassLoader(urls, oldcl);
		Thread.currentThread().setContextClassLoader(cl);
		try {
			Class<Repl> replClass = (Class<Repl>) cl.loadClass(className);
			Constructor<Repl> constructor = replClass.getConstructor(new Class []{Reader.class, Writer.class});
			Repl repl = constructor.newInstance(reader, writer);
			return new ClassloaderRepl(repl, cl);
		} catch (SecurityException e) {
			throw new ReplException(e);
		} catch (NoSuchMethodException e) {
			throw new ReplException(e);
		} catch (IllegalArgumentException e) {
			throw new ReplException(e);
		} catch (InstantiationException e) {
			throw new ReplException(e);
		} catch (IllegalAccessException e) {
			throw new ReplException(e);
		} catch (InvocationTargetException e) {
			throw new ReplException(e);
		} catch (ClassNotFoundException e) {
			throw new ReplException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);	
		}
		
		
	}
}
