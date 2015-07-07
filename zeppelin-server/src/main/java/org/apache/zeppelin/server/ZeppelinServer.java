/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.server;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.servlet.DispatcherType;
import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.rest.InterpreterRestApi;
import org.apache.zeppelin.rest.NotebookRestApi;
import org.apache.zeppelin.rest.ZeppelinRestApi;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.socket.SslWebSocketServerFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wordnik.swagger.jersey.config.JerseyJaxrsConfig;

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

  public static NotebookServer notebookServer;

  public static Server jettyServer;

  private InterpreterFactory replFactory;

  private NotebookRepo notebookRepo;

  public static void main(String[] args) throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    jettyServer = setupJettyServer(conf);
    notebookServer = setupNotebookServer(conf);
    notebookServer.start();

    // REST api
    final ServletContextHandler restApi = setupRestApiContextHandler();
    /** NOTE: Swagger-core is included via the web.xml in zeppelin-web
     * But the rest of swagger is configured here
     */
    final ServletContextHandler swagger = setupSwaggerContextHandler(conf);

    // Web UI
    LOG.info("Create zeppelin websocket on {}:{}", notebookServer.getAddress()
        .getAddress(), notebookServer.getPort());
    final WebAppContext webApp = setupWebAppContext(conf, notebookServer.getPort());
    //Below is commented since zeppelin-docs module is removed.
    //final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    // add all handlers
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    //contexts.setHandlers(new Handler[]{swagger, restApi, webApp, webAppSwagg});
    contexts.setHandlers(new Handler[]{swagger, restApi, webApp});
    jettyServer.setHandler(contexts);

    LOG.info("Start zeppelin server");
    jettyServer.start();
    LOG.info("Started");

    Runtime.getRuntime().addShutdownHook(new Thread(){
      @Override public void run() {
        LOG.info("Shutting down Zeppelin Server ... ");
        try {
          notebook.getInterpreterFactory().close();

          jettyServer.stop();
          notebookServer.stop();
        } catch (Exception e) {
          LOG.error("Error while stopping servlet container", e);
        }
        LOG.info("Bye");
      }
    });


    // when zeppelin is started inside of ide (especially for eclipse)
    // for graceful shutdown, input any key in console window
    if (System.getenv("ZEPPELIN_IDENT_STRING") == null) {
      try {
        System.in.read();
      } catch (IOException e) {
      }
      System.exit(0);
    }

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
    connector.setHost(conf.getServerAddress());
    connector.setPort(conf.getServerPort());

    final Server server = new Server();
    server.addConnector(connector);

    return server;
  }

  private static NotebookServer setupNotebookServer(ZeppelinConfiguration conf)
      throws Exception {

    NotebookServer server = new NotebookServer(conf.getWebSocketAddress(), conf.getWebSocketPort());

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
        "http://" + conf.getServerAddress() + ":" + conf.getServerPort() + "/api");
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
      ZeppelinConfiguration conf, int websocketPort) {

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
      new ServletHolder(new AppScriptServlet(websocketPort)),
      "/*"
    );
    return webApp;
  }

  /**
   * Handles the WebApplication for Swagger-ui.
   *
   * @return WebAppContext with swagger ui context
   */
  /*private static WebAppContext setupWebAppSwagger(
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
  }*/

  public ZeppelinServer() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    this.replFactory = new InterpreterFactory(conf, notebookServer);
    Class<?> notebookStorageClass = getClass().forName(
        conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE));
    Constructor<?> constructor = notebookStorageClass.getConstructor(
        ZeppelinConfiguration.class);
    this.notebookRepo = (NotebookRepo) constructor.newInstance(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, replFactory, notebookServer);
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

