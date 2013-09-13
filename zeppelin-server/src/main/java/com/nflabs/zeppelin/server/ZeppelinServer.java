package com.nflabs.zeppelin.server;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;
import javax.ws.rs.ext.RuntimeDelegate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.rest.ZeppelinImpl;

public class ZeppelinServer extends Application {
	private static final Log LOG = LogFactory.getLog(ZeppelinServer.class);
	
	public static void main(String [] args) throws Exception{
		ZeppelinConfiguration conf = ZeppelinConfiguration.create();

		JAXRSServerFactoryBean jaxrsServerFactory = RuntimeDelegate.getInstance().createEndpoint(new ZeppelinServer(), JAXRSServerFactoryBean.class);
        jaxrsServerFactory.setAddress("http://localhost:8080");
        final org.apache.cxf.endpoint.Server server = jaxrsServerFactory.create();

        LOG.info("Start zeppelin server");
        server.start();
        LOG.info("Started");
        
		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
            	LOG.info("Shutting down Zeppelin Server ... ");
            	try {
					server.stop();
				} catch (Exception e) {
					LOG.error("Error while stopping servlet container", e);
				}
            	
                LOG.info("Bye");
            }
        });

        while (true)
        {
            Thread.sleep(10000);
        }
	}
	
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(ZeppelinImpl.class);
 
        return classes;
    }
}
