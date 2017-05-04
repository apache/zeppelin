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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.Application;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.helium.HeliumBundleFactory;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.rest.ConfigurationsRestApi;
import org.apache.zeppelin.rest.CredentialRestApi;
import org.apache.zeppelin.rest.HeliumRestApi;
import org.apache.zeppelin.rest.InterpreterRestApi;
import org.apache.zeppelin.rest.LoginRestApi;
import org.apache.zeppelin.rest.NotebookRepoRestApi;
import org.apache.zeppelin.rest.NotebookRestApi;
import org.apache.zeppelin.rest.SecurityRestApi;
import org.apache.zeppelin.rest.ZeppelinRestApi;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.utils.SecurityUtils;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
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

/**
 * Main class of Zeppelin.
 */
public class ZeppelinServer extends Application {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

  public static Notebook notebook;
  public static Server jettyWebServer;
  public static NotebookServer notebookWsServer;
  public static Helium helium;

  private final InterpreterSettingManager interpreterSettingManager;
  private SchedulerFactory schedulerFactory;
  private InterpreterFactory replFactory;
  private SearchService noteSearchService;
  private NotebookRepoSync notebookRepo;
  private NotebookAuthorization notebookAuthorization;
  private Credentials credentials;
  private DependencyResolver depResolver;

  public ZeppelinServer() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    this.depResolver = new DependencyResolver(
        conf.getString(ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO));

    InterpreterOutput.limit = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT);

    HeliumApplicationFactory heliumApplicationFactory = new HeliumApplicationFactory();
    HeliumBundleFactory heliumBundleFactory;

    if (isBinaryPackage(conf)) {
      /* In binary package, zeppelin-web/src/app/visualization and zeppelin-web/src/app/tabledata
       * are copied to lib/node_modules/zeppelin-vis, lib/node_modules/zeppelin-tabledata directory.
       * Check zeppelin/zeppelin-distribution/src/assemble/distribution.xml to see how they're
       * packaged into binary package.
       */
      heliumBundleFactory = new HeliumBundleFactory(
          conf,
          null,
          new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO)),
          new File(conf.getRelativeDir("lib/node_modules/zeppelin-tabledata")),
          new File(conf.getRelativeDir("lib/node_modules/zeppelin-vis")),
          new File(conf.getRelativeDir("lib/node_modules/zeppelin-spell")));
    } else {
      heliumBundleFactory = new HeliumBundleFactory(
          conf,
          null,
          new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO)),
          new File(conf.getRelativeDir("zeppelin-web/src/app/tabledata")),
          new File(conf.getRelativeDir("zeppelin-web/src/app/visualization")),
          new File(conf.getRelativeDir("zeppelin-web/src/app/spell")));
    }

    ZeppelinServer.helium = new Helium(
        conf.getHeliumConfPath(),
        conf.getHeliumRegistry(),
        new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO),
            "helium-registry-cache"),
        heliumBundleFactory,
        heliumApplicationFactory);

    // create bundle
    try {
      heliumBundleFactory.buildAllPackages(helium.getBundlePackagesToBundle());
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }

    this.schedulerFactory = new SchedulerFactory();
    this.interpreterSettingManager = new InterpreterSettingManager(conf, depResolver,
        new InterpreterOption(true));
    this.replFactory = new InterpreterFactory(conf, notebookWsServer,
        notebookWsServer, heliumApplicationFactory, depResolver, SecurityUtils.isAuthenticated(),
        interpreterSettingManager);
    this.notebookRepo = new NotebookRepoSync(conf);
    this.noteSearchService = new LuceneSearch();
    this.notebookAuthorization = NotebookAuthorization.init(conf);
    this.credentials = new Credentials(conf.credentialsPersist(), conf.getCredentialsPath());
    notebook = new Notebook(conf,
        notebookRepo, schedulerFactory, replFactory, interpreterSettingManager, notebookWsServer,
            noteSearchService, notebookAuthorization, credentials);

    // to update notebook from application event from remote process.
    heliumApplicationFactory.setNotebook(notebook);
    // to update fire websocket event on application event.
    heliumApplicationFactory.setApplicationEventListener(notebookWsServer);

    notebook.addNotebookEventListener(heliumApplicationFactory);
    notebook.addNotebookEventListener(notebookWsServer.getNotebookInformationListener());
  }

  public static void main(String[] args) throws InterruptedException {

    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    jettyWebServer = setupJettyServer(conf);

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    jettyWebServer.setHandler(contexts);

    // Web UI
    final WebAppContext webApp = setupWebAppContext(contexts, conf);

    // Create `ZeppelinServer` using reflection and setup REST Api
    setupRestApiContextHandler(webApp, conf);

    // Notebook server
    setupNotebookServer(webApp, conf);

    //Below is commented since zeppelin-docs module is removed.
    //final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    LOG.info("Starting zeppelin server");
    try {
      jettyWebServer.start(); //Instantiates ZeppelinServer
      if (conf.getJettyName() != null) {
        org.eclipse.jetty.http.HttpGenerator.setJettyVersion(conf.getJettyName());
      }
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
          notebook.getInterpreterSettingManager().shutdown();
          notebook.close();
          Thread.sleep(3000);
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
    ZeppelinServer.notebook.getInterpreterSettingManager().close();
  }

  private static Server setupJettyServer(ZeppelinConfiguration conf) {

    final Server server = new Server();
    ServerConnector connector;

    if (conf.useSsl()) {
      LOG.debug("Enabling SSL for Zeppelin Server on port " + conf.getServerSslPort());
      HttpConfiguration httpConfig = new HttpConfiguration();
      httpConfig.setSecureScheme("https");
      httpConfig.setSecurePort(conf.getServerSslPort());
      httpConfig.setOutputBufferSize(32768);
      httpConfig.setRequestHeaderSize(8192);
      httpConfig.setResponseHeaderSize(8192);
      httpConfig.setSendServerVersion(true);

      HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
      SecureRequestCustomizer src = new SecureRequestCustomizer();
      // Only with Jetty 9.3.x
      // src.setStsMaxAge(2000);
      // src.setStsIncludeSubDomains(true);
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
    if (conf.useSsl()) {
      connector.setPort(conf.getServerSslPort());
    } else {
      connector.setPort(conf.getServerPort());
    }

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

    if (conf.useClientAuth()) {
      sslContextFactory.setNeedClientAuth(conf.useClientAuth());

      // Set truststore
      sslContextFactory.setTrustStorePath(conf.getTrustStorePath());
      sslContextFactory.setTrustStoreType(conf.getTrustStoreType());
      sslContextFactory.setTrustStorePassword(conf.getTrustStorePassword());
    }

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

    String shiroIniPath = conf.getShiroPath();
    if (!StringUtils.isBlank(shiroIniPath)) {
      webapp.setInitParameter("shiroConfigLocations", new File(shiroIniPath).toURI().toString());
      SecurityUtils.initSecurityManager(shiroIniPath);
      webapp.addFilter(ShiroFilter.class, "/api/*", EnumSet.allOf(DispatcherType.class));
      webapp.addEventListener(new EnvironmentLoaderListener());
    }
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

    webApp.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed",
            Boolean.toString(conf.getBoolean(ConfVars.ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED)));

    return webApp;

  }

  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<>();
    return classes;
  }

  @Override
  public Set<Object> getSingletons() {
    Set<Object> singletons = new HashSet<>();

    /** Rest-api root endpoint */
    ZeppelinRestApi root = new ZeppelinRestApi();
    singletons.add(root);

    NotebookRestApi notebookApi
      = new NotebookRestApi(notebook, notebookWsServer, noteSearchService);
    singletons.add(notebookApi);

    NotebookRepoRestApi notebookRepoApi = new NotebookRepoRestApi(notebookRepo, notebookWsServer);
    singletons.add(notebookRepoApi);

    HeliumRestApi heliumApi = new HeliumRestApi(helium, notebook);
    singletons.add(heliumApi);

    InterpreterRestApi interpreterApi = new InterpreterRestApi(interpreterSettingManager,
        notebookWsServer);
    singletons.add(interpreterApi);

    CredentialRestApi credentialApi = new CredentialRestApi(credentials);
    singletons.add(credentialApi);

    SecurityRestApi securityApi = new SecurityRestApi();
    singletons.add(securityApi);

    LoginRestApi loginRestApi = new LoginRestApi();
    singletons.add(loginRestApi);

    ConfigurationsRestApi settingsApi = new ConfigurationsRestApi(notebook);
    singletons.add(settingsApi);

    return singletons;
  }

  /**
   * Check if it is source build or binary package
   * @return
   */
  private static boolean isBinaryPackage(ZeppelinConfiguration conf) {
    return !new File(conf.getRelativeDir("zeppelin-web")).isDirectory();
  }
}
