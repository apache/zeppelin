package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.protocol.http.WicketServlet;
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
        
		final ServletHolder cxfServletHolder = new ServletHolder( new CXFNonSpringJaxrsServlet() );
		cxfServletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
		cxfServletHolder.setName("rest");
		cxfServletHolder.setForcedPath("rest");
		final ServletContextHandler cxfContext = new ServletContextHandler();
		cxfContext.setSessionHandler(new SessionHandler());
		cxfContext.setContextPath("/cxf");
		cxfContext.addServlet( cxfServletHolder, "/zeppelin/*" ); 

		
		// web
		WebAppContext sch = new WebAppContext();
        sch.setParentLoaderPriority(true);
        File resourcePath = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
        
        if(resourcePath.isDirectory()){
        	System.setProperty("wicket.configuration", "development");
            ServletHolder wicketServletHolder = new ServletHolder(WicketServlet.class);

            wicketServletHolder.setInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM, "com.nflabs.zeppelin.web.WicketApplication");
            wicketServletHolder.setInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        	
            sch.setWar(resourcePath.getAbsolutePath());
            sch.addServlet(wicketServletHolder, "/");
        } else {
        	sch.setWar(resourcePath.getAbsolutePath());
        }

        // add all handlers
	    ContextHandlerCollection contexts = new ContextHandlerCollection();
	    contexts.setHandlers(new Handler[]{cxfContext, sch});
	    server.setHandler(contexts);
	        
	    logger.info("Start zeppelin server");
        server.start();
        logger.info("Started");
        
		Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
            	logger.info("Shutting down Zeppelin Server ... ");
            	try {
					server.stop();
				} catch (Exception e) {
					logger.error("Error while stopping servlet container", e);
				}
            	logger.info("Bye");
            }
        });

        while (true) {
            Thread.sleep(10000);
        }
	}

	private SchedulerFactory schedulerFactory;
	private ZQLSessionManager analyzeSessionManager;
	
	public ZeppelinServer() throws Exception {
		this.schedulerFactory = new SchedulerFactory();
		Z.configure();
		if(Z.conf().getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO")){
			this.analyzeSessionManager = new ZQLSessionManager(schedulerFactory.createOrGetFIFOScheduler("analyze"), Z.fs(), Z.conf().getString(ConfVars.ZEPPELIN_SESSION_DIR));
		} else {
			this.analyzeSessionManager = new ZQLSessionManager(schedulerFactory.createOrGetParallelScheduler("analyze", 100), Z.fs(), Z.conf().getString(ConfVars.ZEPPELIN_SESSION_DIR));
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
