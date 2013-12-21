package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.rest.ZQL;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Z;


public class ZeppelinServer extends Application {
	static Logger logger = LoggerFactory.getLogger(ZeppelinServer.class);
	
	public static void main(String [] args) throws Exception{
        //if (System.getProperty("log4j.configuration") == null) {
		//	Logger.getLogger("com.nflabs.zeppelin").setLevel(Level.DEBUG);
		//	Logger.getRootLogger().setLevel(Level.INFO);
		//}
		ZeppelinConfiguration conf = ZeppelinConfiguration.create();

		int port = conf.getInt(ConfVars.ZEPPELIN_PORT);
        int timeout = 1000*30;
        final Server server = new Server();
        SocketConnector connector = new SocketConnector();

        // Set some timeout options to make debugging easier.
        connector.setMaxIdleTime(timeout);
        connector.setSoLingerTime(-1);
        connector.setPort(port);
        server.addConnector(connector);
        
        //REST api
		final ServletContextHandler cxfContext = setupRestApi(); 
		
		//Web UI
		WebAppContext sch = new WebAppContext();
        File webapp = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
        
        if(webapp.isDirectory()){ // Development mode
            sch.setDescriptor(webapp+"/WEB-INF/web.xml");
            sch.setResourceBase(webapp.getPath());
            sch.setContextPath("/");
            sch.setParentLoaderPriority(true);
        } else {
            sch.setWar(webapp.getAbsolutePath());
        }

        // add all handlers
	    ContextHandlerCollection contexts = new ContextHandlerCollection();
	    contexts.setHandlers(new Handler[]{cxfContext, sch});
	    server.setHandler(contexts);
	        
	    logger.info("Start zeppelin server");
        server.start();
        logger.info("Started");
        
		Runtime.getRuntime().addShutdownHook(new Thread(){
		    @Override public void run() {
		        logger.info("Shutting down Zeppelin Server ... ");
            	try {
					server.stop();
				} catch (Exception e) {
					logger.error("Error while stopping servlet container", e);
				}
            	logger.info("Bye");
            }
        });
		server.join();
	}

    private static ServletContextHandler setupRestApi() {
        final ServletHolder cxfServletHolder = new ServletHolder( new CXFNonSpringJaxrsServlet() );
		cxfServletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
		cxfServletHolder.setName("rest");
		cxfServletHolder.setForcedPath("rest");

		final ServletContextHandler cxfContext = new ServletContextHandler();
		cxfContext.setSessionHandler(new SessionHandler());
		cxfContext.setContextPath("/cxf");
		cxfContext.addServlet( cxfServletHolder, "/zeppelin/*" );
        return cxfContext;
    }

	private SchedulerFactory schedulerFactory;
	private ZQLJobManager analyzeSessionManager;
	
	public ZeppelinServer() throws Exception {
		this.schedulerFactory = new SchedulerFactory();
		Z.configure();
		if(Z.getConf().getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO")){
			this.analyzeSessionManager = new ZQLJobManager(schedulerFactory.createOrGetFIFOScheduler("analyze"), Z.fs(), Z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		} else {
			this.analyzeSessionManager = new ZQLJobManager(schedulerFactory.createOrGetParallelScheduler("analyze", 100), Z.fs(), Z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		}
	}
	
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        return classes;
    }
    
    public java.util.Set<java.lang.Object> getSingletons(){
    	Set<Object> singletons = new HashSet<Object>();
    	
    	ZQL analyze = new ZQL(analyzeSessionManager);
    	singletons.add(analyze);
    	
    	return singletons;
    }

}
