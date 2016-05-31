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

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.rest.*;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.socket.NotebookServer;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.Application;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Main class of Zeppelin.
 */
public class ZeppelinServer extends Application {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

  public static Notebook notebook;
  public static Server jettyWebServer;
  public static NotebookServer notebookWsServer;

  private SchedulerFactory schedulerFactory;
  private InterpreterFactory replFactory;
  private NotebookRepo notebookRepo;
  private SearchService notebookIndex;
  private NotebookAuthorization notebookAuthorization;
  private DependencyResolver depResolver;

  public ZeppelinServer() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    this.depResolver = new DependencyResolver(
        conf.getString(ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO));
    this.schedulerFactory = new SchedulerFactory();
    this.replFactory = new InterpreterFactory(conf, notebookWsServer,
            notebookWsServer, depResolver);
    this.notebookRepo = new NotebookRepoSync(conf);
    this.notebookIndex = new LuceneSearch();
    this.notebookAuthorization = new NotebookAuthorization(conf);
    notebook = new Notebook(conf,
        notebookRepo, schedulerFactory, replFactory, notebookWsServer,
            notebookIndex, notebookAuthorization);
  }

  public static void main(String[] args) throws InterruptedException {

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    jettyWebServer = setupJettyServer(conf);

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    jettyWebServer.setHandler(contexts);

    // Web UI
    final WebAppContext webApp = setupWebAppContext(contexts, conf);

    // REST api
    setupRestApiContextHandler(webApp, conf);

    // Notebook server
    setupNotebookServer(webApp, conf);

    //Below is commented since zeppelin-docs module is removed.
    //final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    LOG.info("Starting zeppelin server");
    try {
      jettyWebServer.start(); //Instantiates ZeppelinServer
    } catch (Exception e) {
      LOG.error("Error while running jettyServer", e);
      System.exit(-1);
    }
    LOG.info("Done, zeppelin server started");

    Runtime.getRuntime().addShutdownHook(new Thread(){
      @Override public void run() {
        LOG.info("Shutting down Zeppelin Server ... ");
        try {
          jettyWebServer.stop();
          notebook.getInterpreterFactory().close();
          notebook.close();
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
        LOG.error("Exception in ZeppelinServer while main ", e);
      }
      System.exit(0);
    }

    jettyWebServer.join();
    ZeppelinServer.notebook.getInterpreterFactory().close();
  }

  private static Server setupJettyServer(ZeppelinConfiguration conf) {

    final Server server = new Server();
    ServerConnector connector;

    if (conf.useSsl()) {

      HttpConfiguration httpConfig = new HttpConfiguration();
      httpConfig.setSecureScheme("https");
      httpConfig.setSecurePort(conf.getServerPort());
      httpConfig.setOutputBufferSize(32768);

      HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
      SecureRequestCustomizer src = new SecureRequestCustomizer();
      // Only with Jetty 9.3.x
//      src.setStsMaxAge(2000);
//      src.setStsIncludeSubDomains(true);
      httpsConfig.addCustomizer(src);

      connector = new ServerConnector(
              server,
              new SslConnectionFactory(getSslContextFactory(conf), HttpVersion.HTTP_1_1.asString()),
              new HttpConnectionFactory(httpsConfig));


    } else {

      connector = new ServerConnector(server);

    }

    // Set some timeout options to make debugging easier.
    int timeout = 1000 * 30;
    connector.setIdleTimeout(timeout);
    connector.setSoLingerTime(-1);
    connector.setHost(conf.getServerAddress());
    connector.setPort(conf.getServerPort());

    server.addConnector(connector);

    return server;
  }

  private static void setupNotebookServer(WebAppContext webapp,
                                          ZeppelinConfiguration conf) {
    notebookWsServer = new NotebookServer();
    String maxTextMessageSize = conf.getWebsocketMaxTextMessageSize();
    final ServletHolder servletHolder = new ServletHolder(notebookWsServer);
    servletHolder.setInitParameter("maxTextMessageSize", maxTextMessageSize);

    final ServletContextHandler cxfContext = new ServletContextHandler(
        ServletContextHandler.SESSIONS);

    webapp.addServlet(servletHolder, "/ws/*");
  }

  private static SslContextFactory getSslContextFactory(ZeppelinConfiguration conf) {
    SslContextFactory sslContextFactory = new SslContextFactory();

    // Set keystore
    sslContextFactory.setKeyStorePath(conf.getKeyStorePath());
    sslContextFactory.setKeyStoreType(conf.getKeyStoreType());
    sslContextFactory.setKeyStorePassword(conf.getKeyStorePassword());
    sslContextFactory.setKeyManagerPassword(conf.getKeyManagerPassword());

    // Set truststore
    sslContextFactory.setTrustStorePath(conf.getTrustStorePath());
    sslContextFactory.setTrustStoreType(conf.getTrustStoreType());
    sslContextFactory.setTrustStorePassword(conf.getTrustStorePassword());

    sslContextFactory.setNeedClientAuth(conf.useClientAuth());

    return sslContextFactory;
  }

  private static void setupRestApiContextHandler(WebAppContext webapp,
                                                 ZeppelinConfiguration conf) {

    final ServletHolder cxfServletHolder = new ServletHolder(new CXFNonSpringJaxrsServlet());
    cxfServletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
    cxfServletHolder.setName("rest");
    cxfServletHolder.setForcedPath("rest");

    webapp.setSessionHandler(new SessionHandler());
    webapp.addServlet(cxfServletHolder, "/api/*");

    webapp.setInitParameter("shiroConfigLocations",
        new File(conf.getShiroPath()).toURI().toString());

    webapp.addFilter(org.apache.shiro.web.servlet.ShiroFilter.class, "/api/*",
        EnumSet.allOf(DispatcherType.class));

    webapp.addEventListener(new org.apache.shiro.web.env.EnvironmentLoaderListener());

  }

  private static WebAppContext setupWebAppContext(ContextHandlerCollection contexts,
                                                  ZeppelinConfiguration conf) {

    WebAppContext webApp = new WebAppContext();
    webApp.setContextPath(conf.getServerContextPath());
    File warPath = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
    if (warPath.isDirectory()) {
      // Development mode, read from FS
      // webApp.setDescriptor(warPath+"/WEB-INF/web.xml");
      webApp.setResourceBase(warPath.getPath());
      webApp.setParentLoaderPriority(true);
    } else {
      // use packaged WAR
      webApp.setWar(warPath.getAbsolutePath());
      File warTempDirectory = new File(conf.getRelativeDir(ConfVars.ZEPPELIN_WAR_TEMPDIR));
      warTempDirectory.mkdir();
      LOG.info("ZeppelinServer Webapp path: {}", warTempDirectory.getPath());
      webApp.setTempDirectory(warTempDirectory);
    }
    // Explicit bind to root
    webApp.addServlet(new ServletHolder(new DefaultServlet()), "/*");
    contexts.addHandler(webApp);

    webApp.addFilter(new FilterHolder(CorsFilter.class), "/*",
        EnumSet.allOf(DispatcherType.class));

    return webApp;

  }

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<Class<?>>();
    return classes;
  }

  @Override
  public Set<Object> getSingletons() {
    Set<Object> singletons = new HashSet<>();

    /** Rest-api root endpoint */
    ZeppelinRestApi root = new ZeppelinRestApi();
    singletons.add(root);

    NotebookRestApi notebookApi = new NotebookRestApi(notebook, notebookWsServer, notebookIndex);
    singletons.add(notebookApi);

    InterpreterRestApi interpreterApi = new InterpreterRestApi(replFactory);
    singletons.add(interpreterApi);

    SecurityRestApi securityApi = new SecurityRestApi();
    singletons.add(securityApi);

    LoginRestApi loginRestApi = new LoginRestApi();
    singletons.add(loginRestApi);

    ConfigurationsRestApi settingsApi = new ConfigurationsRestApi(notebook);
    singletons.add(settingsApi);

    return singletons;
  }
}

