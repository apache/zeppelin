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
import com.nflabs.zeppelin.rest.ZAN;
import com.nflabs.zeppelin.rest.ZQL;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.zengine.Zengine;


public class ZeppelinServer extends Application {
	private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

	private SchedulerFactory schedulerFactory;
	private ZQLJobManager analyzeSessionManager;
	private ZANJobManager zanJobManager;
	private com.nflabs.zeppelin.zan.ZAN zan;

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
		final ServletContextHandler restApi = setupRestApiContextHandler(); 
		//Web UI
		final WebAppContext webApp = setupWebAppContext(conf);

        // add all handlers
	    ContextHandlerCollection contexts = new ContextHandlerCollection();
	    contexts.setHandlers(new Handler[]{restApi, webApp});
	    server.setHandler(contexts);
	        
	    LOG.info("Start zeppelin server");
        server.start();
        LOG.info("Started");
        
		Runtime.getRuntime().addShutdownHook(new Thread(){
		    @Override public void run() {
		        LOG.info("Shutting down Zeppelin Server ... ");
            	try {
					server.stop();
				} catch (Exception e) {
					LOG.error("Error while stopping servlet container", e);
				}
            	LOG.info("Bye");
            }
        });
		server.join();
	}

    private static WebAppContext setupWebAppContext(ZeppelinConfiguration conf) {
        WebAppContext webApp = new WebAppContext();
        File webapp = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
        
        if(webapp.isDirectory()){ // Development mode
            webApp.setDescriptor(webapp+"/WEB-INF/web.xml");
            webApp.setResourceBase(webapp.getPath());
            webApp.setContextPath("/");
            webApp.setParentLoaderPriority(true);
        } else {
            webApp.setWar(webapp.getAbsolutePath());
        }
        return webApp;
    }

    private static ServletContextHandler setupRestApiContextHandler() {
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
	
	public ZeppelinServer() throws Exception {
		this.schedulerFactory = new SchedulerFactory();

		Zengine z = new Zengine();
        z.configure();

        if(z.getConf().getString(ConfVars.ZEPPELIN_JOB_SCHEDULER).equals("FIFO")){
			this.analyzeSessionManager = new ZQLJobManager(schedulerFactory.createOrGetFIFOScheduler("analyze"), z.fs(), z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		} else {
			this.analyzeSessionManager = new ZQLJobManager(schedulerFactory.createOrGetParallelScheduler("analyze", 100), z.fs(), z.getConf().getString(ConfVars.ZEPPELIN_JOB_DIR));
		}		
		
		this.zan = new com.nflabs.zeppelin.zan.ZAN(z.getConf().getString(ConfVars.ZEPPELIN_ZAN_REPO),
												   z.getConf().getString(ConfVars.ZEPPELIN_ZAN_LOCAL_REPO),
												   z.getConf().getString(ConfVars.ZEPPELIN_ZAN_SHARED_REPO),
												   z.fs());
		
		this.zanJobManager = new ZANJobManager(zan, schedulerFactory.createOrGetFIFOScheduler("analyze"));
	}
	
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        return classes;
    }
    
    public java.util.Set<java.lang.Object> getSingletons(){
    	Set<Object> singletons = new HashSet<Object>();
    	
    	ZQL analyze = new ZQL(analyzeSessionManager);
    	singletons.add(analyze);
    	
    	ZAN zan = new ZAN(this.zan, this.zanJobManager);
    	singletons.add(zan);
    	
    	return singletons;
    }

}
