package com.nflabs.zeppelin.server;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.notebook.Notebook;
import com.nflabs.zeppelin.rest.InterpreterRestApi;
import com.nflabs.zeppelin.rest.NotebookRestApi;
import com.nflabs.zeppelin.rest.ZeppelinRestApi;
import com.nflabs.zeppelin.socket.NotebookServer;
import com.nflabs.zeppelin.socket.SslWebSocketServerFactory;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.wordnik.swagger.jersey.config.JerseyJaxrsConfig;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.servlet.DispatcherType;
import javax.ws.rs.core.Application;

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

  static NotebookServer notebookServer;

  private InterpreterFactory replFactory;

  public static void main(String[] args) throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    final Server jettyServer = setupJettyServer(conf);
    notebookServer = setupNotebookServer(conf);

    // REST api
    final ServletContextHandler restApi = setupRestApiContextHandler();
    /** NOTE: Swagger-core is included via the web.xml in zeppelin-web
     * But the rest of swagger is configured here
     */
    final ServletContextHandler swagger = setupSwaggerContextHandler(conf);

    // Web UI
    final WebAppContext webApp = setupWebAppContext(conf);
    final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    // add all handlers
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    contexts.setHandlers(new Handler[]{swagger, restApi, webApp, webAppSwagg});
    jettyServer.setHandler(contexts);

    notebookServer.start();
    LOG.info("Start zeppelin server");
    jettyServer.start();
    LOG.info("Started");

    Runtime.getRuntime().addShutdownHook(new Thread(){
      @Override public void run() {
        LOG.info("Shutting down Zeppelin Server ... ");
        try {
          jettyServer.stop();
          notebookServer.stop();
        } catch (Exception e) {
          LOG.error("Error while stopping servlet container", e);
        }
        LOG.info("Bye");
      }
    });
    jettyServer.join();
  }

  private static Server setupJettyServer(ZeppelinConfiguration conf)
      throws Exception {

    SocketConnector connector;
    if (conf.useSsl()) {
      connector = new SslSocketConnector(getSslContextFactory(conf));
    }
    else {
      connector = new SocketConnector();
    }

    // Set some timeout options to make debugging easier.
    int timeout = 1000 * 30;
    connector.setMaxIdleTime(timeout);
    connector.setSoLingerTime(-1);
    connector.setPort(conf.getServerPort());

    final Server server = new Server();
    server.addConnector(connector);

    return server;
  }

  private static NotebookServer setupNotebookServer(ZeppelinConfiguration conf)
      throws Exception {

    NotebookServer server = new NotebookServer(conf.getWebSocketPort());

    // Default WebSocketServer uses unencrypted connector, so only need to
    // change the connector if SSL should be used.
    if (conf.useSsl()) {
      SslWebSocketServerFactory wsf = new SslWebSocketServerFactory(getSslContext(conf));
      wsf.setNeedClientAuth(conf.useClientAuth());
      server.setWebSocketFactory(wsf);
    }

    return server;
  }

  private static SslContextFactory getSslContextFactory(ZeppelinConfiguration conf)
      throws Exception {

    // Note that the API for the SslContextFactory is different for
    // Jetty version 9
    SslContextFactory sslContextFactory = new SslContextFactory();

    // Set keystore
    sslContextFactory.setKeyStore(conf.getKeyStorePath());
    sslContextFactory.setKeyStoreType(conf.getKeyStoreType());
    sslContextFactory.setKeyStorePassword(conf.getKeyStorePassword());
    sslContextFactory.setKeyManagerPassword(conf.getKeyManagerPassword());

    // Set truststore
    sslContextFactory.setTrustStore(conf.getTrustStorePath());
    sslContextFactory.setTrustStoreType(conf.getTrustStoreType());
    sslContextFactory.setTrustStorePassword(conf.getTrustStorePassword());

    sslContextFactory.setNeedClientAuth(conf.useClientAuth());

    return sslContextFactory;
  }

  private static SSLContext getSslContext(ZeppelinConfiguration conf)
      throws Exception {

    SslContextFactory scf = getSslContextFactory(conf);
    if (!scf.isStarted()) {
      scf.start();
    }
    return scf.getSslContext();
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
    
    cxfContext.addFilter(new FilterHolder(CorsFilter.class), "/*",
        EnumSet.allOf(DispatcherType.class));
    return cxfContext;
  }

  /**
   * Swagger core handler - Needed for the RestFul api documentation.
   *
   * @return ServletContextHandler of Swagger
   */
  private static ServletContextHandler setupSwaggerContextHandler(
    ZeppelinConfiguration conf) {
  
    // Configure Swagger-core
    final ServletHolder swaggerServlet =
        new ServletHolder(new JerseyJaxrsConfig());
    swaggerServlet.setName("JerseyJaxrsConfig");
    swaggerServlet.setInitParameter("api.version", "1.0.0");
    swaggerServlet.setInitParameter(
        "swagger.api.basepath", 
        "http://localhost:" + conf.getServerPort() + "/api");
    swaggerServlet.setInitOrder(2);

    // Setup the handler
    final ServletContextHandler handler = new ServletContextHandler();
    handler.setSessionHandler(new SessionHandler());
    // Bind Swagger-core to the url HOST/api-docs
    handler.addServlet(swaggerServlet, "/api-docs/*");

    // And we are done
    return handler;
  }

  private static WebAppContext setupWebAppContext(
      ZeppelinConfiguration conf) {
    
    WebAppContext webApp = new WebAppContext();
    File warPath = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
    if (warPath.isDirectory()) {
      // Development mode, read from FS
      // webApp.setDescriptor(warPath+"/WEB-INF/web.xml");
      webApp.setResourceBase(warPath.getPath());
      webApp.setContextPath("/");
      webApp.setParentLoaderPriority(true);
    } else {
      // use packaged WAR
      webApp.setWar(warPath.getAbsolutePath());
    }
    // Explicit bind to root
    webApp.addServlet(
      new ServletHolder(new AppScriptServlet(conf.getWebSocketPort())),
      "/*"
    );
    return webApp;
  }

  /**
   * Handles the WebApplication for Swagger-ui.
   *
   * @return WebAppContext with swagger ui context
   */
  private static WebAppContext setupWebAppSwagger(
      ZeppelinConfiguration conf) {

    WebAppContext webApp = new WebAppContext();
    File warPath = new File(conf.getString(ConfVars.ZEPPELIN_API_WAR));

    if (warPath.isDirectory()) {
      webApp.setResourceBase(warPath.getPath());
    } else {
      webApp.setWar(warPath.getAbsolutePath());
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
    notebook = new Notebook(conf, schedulerFactory, replFactory, notebookServer);
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
    
    NotebookRestApi notebookApi = new NotebookRestApi(notebook);
    singletons.add(notebookApi);

    InterpreterRestApi interpreterApi = new InterpreterRestApi(replFactory);
    singletons.add(interpreterApi);

    return singletons;
  }
}

