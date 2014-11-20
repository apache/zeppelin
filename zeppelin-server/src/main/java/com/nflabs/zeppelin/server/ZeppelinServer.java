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
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.notebook.Notebook;
import com.nflabs.zeppelin.rest.ZeppelinRestApi;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Main class of Zeppelin.
 * 
 * @author Leemoonsoo
 *
 */
public class ZeppelinServer extends Application {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

  private SchedulerFactory schedulerFactory;
  public static Notebook notebook;
  static com.nflabs.zeppelin.socket.NotebookServer websocket;

  private InterpreterFactory replFactory;

  public static void main(String[] args) throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    int port = conf.getInt(ConfVars.ZEPPELIN_PORT);
    final Server server = setupJettyServer(port);
    websocket = new com.nflabs.zeppelin.socket.NotebookServer(port + 1);

    // REST api
    final ServletContextHandler restApi = setupRestApiContextHandler();
    /**
     * NOTE: Swagger-core is included via the web.xml in zeppelin-web But the rest of swagger is
     * configured here
     */
    final ServletContextHandler swagger = setupSwaggerContextHandler(port);
    // Web UI
    final WebAppContext webApp = setupWebAppContext(conf);
    final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    // add all handlers
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.setHandlers(new Handler[] {swagger, restApi, webApp, webAppSwagg});
    server.setHandler(contexts);


    websocket.start();
    LOG.info("Start zeppelin server");
    server.start();
    LOG.info("Started");

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        LOG.info("Shutting down Zeppelin Server ... ");
        try {
          server.stop();
          websocket.stop();
        } catch (Exception e) {
          LOG.error("Error while stopping servlet container", e);
        }
        LOG.info("Bye");
      }
    });
    server.join();
  }

  private static Server setupJettyServer(int port) {
    int timeout = 1000 * 30;
    final Server server = new Server();
    SocketConnector connector = new SocketConnector();

    // Set some timeout options to make debugging easier.
    connector.setMaxIdleTime(timeout);
    connector.setSoLingerTime(-1);
    connector.setPort(port);
    server.addConnector(connector);
    return server;
  }

  private static ServletContextHandler setupRestApiContextHandler() {
    final ServletHolder cxfServletHolder = new ServletHolder(new CXFNonSpringJaxrsServlet());
    cxfServletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
    cxfServletHolder.setName("rest");
    cxfServletHolder.setForcedPath("rest");

    final ServletContextHandler cxfContext = new ServletContextHandler();
    cxfContext.setSessionHandler(new SessionHandler());
    cxfContext.setContextPath("/api");
    cxfContext.addServlet(cxfServletHolder, "/*");
    return cxfContext;
  }

  /**
   * Swagger core handler - Needed for the RestFul api documentation.
   *
   * @return ServletContextHandler of Swagger
   */
  private static ServletContextHandler setupSwaggerContextHandler(int port) {
    // Configure Swagger-core
    final ServletHolder swaggerServlet =
        new ServletHolder(new com.wordnik.swagger.jersey.config.JerseyJaxrsConfig());
    swaggerServlet.setName("JerseyJaxrsConfig");
    swaggerServlet.setInitParameter("api.version", "1.0.0");
    swaggerServlet.setInitParameter("swagger.api.basepath", "http://localhost:" + port + "/api");
    swaggerServlet.setInitOrder(2);

    // Setup the handler
    final ServletContextHandler handler = new ServletContextHandler();
    handler.setSessionHandler(new SessionHandler());
    // Bind Swagger-core to the url HOST/api-docs
    handler.addServlet(swaggerServlet, "/api-docs/*");

    // And we are done
    return handler;
  }

  private static WebAppContext setupWebAppContext(ZeppelinConfiguration conf) {
    WebAppContext webApp = new WebAppContext();
    File webapp = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
    if (webapp.isDirectory()) { // Development mode, read from FS
      // webApp.setDescriptor(webapp+"/WEB-INF/web.xml");
      webApp.setResourceBase(webapp.getPath());
      webApp.setContextPath("/");
      webApp.setParentLoaderPriority(true);
    } else { // use packaged WAR
      webApp.setWar(webapp.getAbsolutePath());
    }
    // Explicit bind to root
    webApp.addServlet(new ServletHolder(new DefaultServlet()), "/*");
    return webApp;
  }

  /**
   * Handles the WebApplication for Swagger-ui.
   *
   * @return WebAppContext with swagger ui context
   */
  private static WebAppContext setupWebAppSwagger(ZeppelinConfiguration conf) {
    WebAppContext webApp = new WebAppContext();
    File webapp = new File(conf.getString(ConfVars.ZEPPELIN_API_WAR));

    if (webapp.isDirectory()) {
      webApp.setResourceBase(webapp.getPath());
    } else {
      webApp.setWar(webapp.getAbsolutePath());
    }
    webApp.setContextPath("/docs");
    webApp.setParentLoaderPriority(true);
    // Bind swagger-ui to the path HOST/docs
    webApp.addServlet(new ServletHolder(new DefaultServlet()), "/docs/*");
    return webApp;
  }

  public ZeppelinServer() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    this.replFactory = new InterpreterFactory(conf);
    notebook = new Notebook(conf, schedulerFactory, replFactory, websocket);
  }

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<Class<?>>();
    return classes;
  }

  @Override
  public java.util.Set<java.lang.Object> getSingletons() {
    Set<Object> singletons = new HashSet<Object>();

    /** Rest-api root endpoint */
    ZeppelinRestApi root = new ZeppelinRestApi();
    singletons.add(root);

    return singletons;
  }

}
