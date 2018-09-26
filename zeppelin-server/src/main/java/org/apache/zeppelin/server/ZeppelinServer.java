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
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.EnumSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.servlet.DispatcherType;
import javax.ws.rs.ApplicationPath;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.helium.HeliumBundleFactory;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.rest.exception.WebApplicationExceptionMapper;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.service.AdminService;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.InterpreterService;
import org.apache.zeppelin.service.NotebookService;
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
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main class of Zeppelin. */
@ApplicationPath("/api")
public class ZeppelinServer extends ResourceConfig {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

  public static Notebook notebook;
  public static Server jettyWebServer;
  public static NotebookServer notebookWsServer;
  public static Helium helium;

  private final InterpreterSettingManager interpreterSettingManager;
  private InterpreterFactory replFactory;
  private SearchService noteSearchService;
  private NotebookRepoSync notebookRepo;
  private NotebookAuthorization notebookAuthorization;
  private Credentials credentials;

  @Inject
  public ZeppelinServer(ServiceLocator serviceLocator) throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    if (conf.getShiroPath().length() > 0) {
      try {
        Collection<Realm> realms =
            ((DefaultWebSecurityManager) org.apache.shiro.SecurityUtils.getSecurityManager())
                .getRealms();
        if (realms.size() > 1) {
          Boolean isIniRealmEnabled = false;
          for (Object realm : realms) {
            if (realm instanceof IniRealm && ((IniRealm) realm).getIni().get("users") != null) {
              isIniRealmEnabled = true;
              break;
            }
          }
          if (isIniRealmEnabled) {
            throw new Exception(
                "IniRealm/password based auth mechanisms should be exclusive. "
                    + "Consider removing [users] block from shiro.ini");
          }
        }
      } catch (UnavailableSecurityManagerException e) {
        LOG.error("Failed to initialise shiro configuraion", e);
      }
    }

    InterpreterOutput.limit = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT);

    HeliumApplicationFactory heliumApplicationFactory = new HeliumApplicationFactory();
    HeliumBundleFactory heliumBundleFactory;

    if (isBinaryPackage(conf)) {
      /* In binary package, zeppelin-web/src/app/visualization and zeppelin-web/src/app/tabledata
       * are copied to lib/node_modules/zeppelin-vis, lib/node_modules/zeppelin-tabledata directory.
       * Check zeppelin/zeppelin-distribution/src/assemble/distribution.xml to see how they're
       * packaged into binary package.
       */
      heliumBundleFactory =
          new HeliumBundleFactory(
              conf,
              null,
              new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO)),
              new File(conf.getRelativeDir("lib/node_modules/zeppelin-tabledata")),
              new File(conf.getRelativeDir("lib/node_modules/zeppelin-vis")),
              new File(conf.getRelativeDir("lib/node_modules/zeppelin-spell")));
    } else {
      heliumBundleFactory =
          new HeliumBundleFactory(
              conf,
              null,
              new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO)),
              new File(conf.getRelativeDir("zeppelin-web/src/app/tabledata")),
              new File(conf.getRelativeDir("zeppelin-web/src/app/visualization")),
              new File(conf.getRelativeDir("zeppelin-web/src/app/spell")));
    }

    this.interpreterSettingManager =
        new InterpreterSettingManager(conf, notebookWsServer, notebookWsServer, notebookWsServer);
    this.replFactory = new InterpreterFactory(interpreterSettingManager);
    this.notebookRepo = new NotebookRepoSync(conf);
    this.noteSearchService = new LuceneSearch(conf);
    this.notebookAuthorization = NotebookAuthorization.getInstance();
    this.credentials =
        new Credentials(
            conf.credentialsPersist(), conf.getCredentialsPath(), conf.getCredentialsEncryptKey());
    notebook =
        new Notebook(
            conf,
            notebookRepo,
            replFactory,
            interpreterSettingManager,
            noteSearchService,
            notebookAuthorization,
            credentials);

    ZeppelinServer.helium =
        new Helium(
            conf.getHeliumConfPath(),
            conf.getHeliumRegistry(),
            new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO), "helium-registry-cache"),
            heliumBundleFactory,
            heliumApplicationFactory,
            interpreterSettingManager);

    // create bundle
    try {
      heliumBundleFactory.buildAllPackages(helium.getBundlePackagesToBundle());
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }

    // to update notebook from application event from remote process.
    heliumApplicationFactory.setNotebook(notebook);
    // to update fire websocket event on application event.
    heliumApplicationFactory.setApplicationEventListener(notebookWsServer);

    notebook.addNotebookEventListener(heliumApplicationFactory);
    notebook.addNotebookEventListener(notebookWsServer);


    // Register MBean
    if ("true".equals(System.getenv("ZEPPELIN_JMX_ENABLE"))) {
      MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      try {
        mBeanServer.registerMBean(
            notebookWsServer,
            new ObjectName("org.apache.zeppelin:type=" + NotebookServer.class.getSimpleName()));
        mBeanServer.registerMBean(
            interpreterSettingManager,
            new ObjectName(
                "org.apache.zeppelin:type=" + InterpreterSettingManager.class.getSimpleName()));
      } catch (InstanceAlreadyExistsException
          | MBeanRegistrationException
          | MalformedObjectNameException
          | NotCompliantMBeanException e) {
        LOG.error("Failed to register MBeans", e);
      }
    }

    ServiceLocatorUtilities.enableImmediateScope(serviceLocator);

    register(
        new AbstractBinder() {
          @Override
          protected void configure() {
            bind(notebookRepo).to(NotebookRepoSync.class).in(Singleton.class);
            bind(notebookWsServer).to(NotebookServer.class).in(Singleton.class);
            bind(notebook).to(Notebook.class).in(Singleton.class);
            bind(noteSearchService).to(SearchService.class).in(Singleton.class);
            bind(helium).to(Helium.class).in(Singleton.class);
            bind(conf).to(ZeppelinConfiguration.class).in(Singleton.class);
            bind(interpreterSettingManager).to(InterpreterSettingManager.class).in(Singleton.class);
            bind(InterpreterService.class).to(InterpreterService.class).in(Immediate.class);
            bind(credentials).to(Credentials.class).in(Singleton.class);
            bind(GsonProvider.class).to(GsonProvider.class).in(Singleton.class);
            bind(WebApplicationExceptionMapper.class)
                .to(WebApplicationExceptionMapper.class)
                .in(Singleton.class);
            bind(AdminService.class).to(AdminService.class).in(Immediate.class);
            bind(notebookAuthorization).to(NotebookAuthorization.class).in(Singleton.class);
            bind(ConfigurationService.class).to(ConfigurationService.class).in(Immediate.class);
            bind(NotebookService.class).to(NotebookService.class).in(Immediate.class);
          }
        });
    packages("org.apache.zeppelin.rest");
  }

  public static void main(String[] args) throws InterruptedException {
    final ZeppelinConfiguration conf = ZeppelinConfiguration.create();
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

    // Below is commented since zeppelin-docs module is removed.
    // final WebAppContext webAppSwagg = setupWebAppSwagger(conf);

    LOG.info("Starting zeppelin server");
    try {
      jettyWebServer.start(); // Instantiates ZeppelinServer
      if (conf.getJettyName() != null) {
        org.eclipse.jetty.http.HttpGenerator.setJettyVersion(conf.getJettyName());
      }
    } catch (Exception e) {
      LOG.error("Error while running jettyServer", e);
      System.exit(-1);
    }
    LOG.info("Done, zeppelin server started");

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                LOG.info("Shutting down Zeppelin Server ... ");
                try {
                  jettyWebServer.stop();
                  if (!conf.isRecoveryEnabled()) {
                    ZeppelinServer.notebook.getInterpreterSettingManager().close();
                  }
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
    if (!conf.isRecoveryEnabled()) {
      ZeppelinServer.notebook.getInterpreterSettingManager().close();
    }
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
      httpConfig.setResponseHeaderSize(8192);
      httpConfig.setSendServerVersion(true);

      HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
      SecureRequestCustomizer src = new SecureRequestCustomizer();
      // Only with Jetty 9.3.x
      // src.setStsMaxAge(2000);
      // src.setStsIncludeSubDomains(true);
      httpsConfig.addCustomizer(src);

      connector =
          new ServerConnector(
              server,
              new SslConnectionFactory(getSslContextFactory(conf), HttpVersion.HTTP_1_1.asString()),
              new HttpConnectionFactory(httpsConfig));
    } else {
      connector = new ServerConnector(server);
    }

    configureRequestHeaderSize(conf, connector);
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

  private static void configureRequestHeaderSize(
      ZeppelinConfiguration conf, ServerConnector connector) {
    HttpConnectionFactory cf =
        (HttpConnectionFactory) connector.getConnectionFactory(HttpVersion.HTTP_1_1.toString());
    int requestHeaderSize = conf.getJettyRequestHeaderSize();
    cf.getHttpConfiguration().setRequestHeaderSize(requestHeaderSize);
  }

  private static void setupNotebookServer(WebAppContext webapp, ZeppelinConfiguration conf) {
    notebookWsServer = new NotebookServer();
    String maxTextMessageSize = conf.getWebsocketMaxTextMessageSize();
    final ServletHolder servletHolder = new ServletHolder(notebookWsServer);
    servletHolder.setInitParameter("maxTextMessageSize", maxTextMessageSize);

    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);

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

  private static void setupRestApiContextHandler(WebAppContext webapp, ZeppelinConfiguration conf) {
    final ServletHolder servletHolder =
        new ServletHolder(new org.glassfish.jersey.servlet.ServletContainer());

    servletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
    servletHolder.setName("rest");
    servletHolder.setForcedPath("rest");

    webapp.setSessionHandler(new SessionHandler());
    webapp.addServlet(servletHolder, "/api/*");

    String shiroIniPath = conf.getShiroPath();
    if (!StringUtils.isBlank(shiroIniPath)) {
      webapp.setInitParameter("shiroConfigLocations", new File(shiroIniPath).toURI().toString());
      SecurityUtils.setIsEnabled(true);
      webapp
          .addFilter(ShiroKerberosAuthenticationFilter.class, "/api/*",
              EnumSet.allOf(DispatcherType.class))
          .setInitParameter("staticSecurityManagerEnabled", "true");
      webapp.addEventListener(new EnvironmentLoaderListener());
    }
  }

  private static WebAppContext setupWebAppContext(
      ContextHandlerCollection contexts, ZeppelinConfiguration conf) {
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

    webApp.addFilter(new FilterHolder(CorsFilter.class), "/*", EnumSet.allOf(DispatcherType.class));

    webApp.setInitParameter(
        "org.eclipse.jetty.servlet.Default.dirAllowed",
        Boolean.toString(conf.getBoolean(ConfVars.ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED)));

    return webApp;
  }

  /**
   * Check if it is source build or binary package.
   *
   * @return
   */
  private static boolean isBinaryPackage(ZeppelinConfiguration conf) {
    return !new File(conf.getRelativeDir("zeppelin-web")).isDirectory();
  }
}
