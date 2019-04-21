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

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.commons.lang.StringUtils;
import org.apache.directory.api.util.Strings;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.zeppelin.cluster.ClusterManager;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.helium.HeliumBundleFactory;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.ClusterManagerService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.scheduler.NoSchedulerService;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.rest.exception.WebApplicationExceptionMapper;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.service.*;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
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
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main class of Zeppelin. */
public class ZeppelinServer extends ResourceConfig {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinServer.class);

  public static Server jettyWebServer;
  public static ServiceLocator sharedServiceLocator;

  @Inject
  public ZeppelinServer() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    InterpreterOutput.limit = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT);

    packages("org.apache.zeppelin.rest");
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    final ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    conf.setProperty("args", args);

    jettyWebServer = setupJettyServer(conf);

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    jettyWebServer.setHandler(contexts);

    // Web UI
    final WebAppContext webApp = setupWebAppContext(contexts, conf);

    sharedServiceLocator = ServiceLocatorFactory.getInstance().create("shared-locator");
    ServiceLocatorUtilities.enableImmediateScope(sharedServiceLocator);
    ServiceLocatorUtilities.bind(
        sharedServiceLocator,
        new AbstractBinder() {
          @Override
          protected void configure() {
            Credentials credentials =
                new Credentials(
                    conf.credentialsPersist(),
                    conf.getCredentialsPath(),
                    conf.getCredentialsEncryptKey());

            bindAsContract(InterpreterFactory.class).in(Singleton.class);
            bindAsContract(NotebookRepoSync.class).to(NotebookRepo.class).in(Singleton.class);
            bind(LuceneSearch.class).to(SearchService.class).in(Singleton.class);
            bindAsContract(Helium.class).in(Singleton.class);
            bind(conf).to(ZeppelinConfiguration.class);
            bindAsContract(InterpreterSettingManager.class).in(Singleton.class);
            bindAsContract(InterpreterService.class).in(Singleton.class);
            bind(credentials).to(Credentials.class);
            bindAsContract(GsonProvider.class).in(Singleton.class);
            bindAsContract(WebApplicationExceptionMapper.class).in(Singleton.class);
            bindAsContract(AdminService.class).in(Singleton.class);
            bindAsContract(AuthorizationService.class).to(Singleton.class);
            // TODO(jl): Will make it more beautiful
            if (!StringUtils.isBlank(conf.getShiroPath())) {
              bind(ShiroAuthenticationService.class).to(AuthenticationService.class).in(Singleton.class);
            } else {
              // TODO(jl): Will be added more type
              bind(NoAuthenticationService.class).to(AuthenticationService.class).in(Singleton.class);
            }
            bindAsContract(HeliumBundleFactory.class).in(Singleton.class);
            bindAsContract(HeliumApplicationFactory.class).in(Singleton.class);
            bindAsContract(ConfigurationService.class).in(Singleton.class);
            bindAsContract(NotebookService.class).in(Singleton.class);
            bindAsContract(JobManagerService.class).in(Singleton.class);
            bindAsContract(Notebook.class).in(Singleton.class);
            bindAsContract(NoteServingTaskManagerService.class).in(Singleton.class);
            bindAsContract(NoteTestTaskManagerService.class).in(Singleton.class);
            bindAsContract(NotebookServer.class)
                .to(AngularObjectRegistryListener.class)
                .to(RemoteInterpreterProcessListener.class)
                .to(ApplicationEventListener.class)
                .to(NoteEventListener.class)
                .to(WebSocketServlet.class)
                .in(Singleton.class);
            bindAsContract(ClusterManagerServer.class)
                .to(ClusterManager.class)
                .to(ClusterManagerService.Iface.class)
                .in(Singleton.class);
            if (conf.isZeppelinNotebookCronEnable()) {
              bind(QuartzSchedulerService.class).to(SchedulerService.class).in(Singleton.class);
            } else {
              bind(NoSchedulerService.class).to(SchedulerService.class).in(Singleton.class);
            }
          }
        });

    webApp.addEventListener(
        new ServletContextListener() {
          @Override
          public void contextInitialized(ServletContextEvent servletContextEvent) {
            servletContextEvent
                .getServletContext()
                .setAttribute(ServletProperties.SERVICE_LOCATOR, sharedServiceLocator);
          }

          @Override
          public void contextDestroyed(ServletContextEvent servletContextEvent) {}
        });

    // Create `ZeppelinServer` using reflection and setup REST Api
    setupRestApiContextHandler(webApp, conf);

    // Notebook server
    setupNotebookServer(webApp, conf, sharedServiceLocator);

    // Cluster Manager Server
    setupClusterManagerServer(sharedServiceLocator);

    // JMX Enable
    Stream.of("ZEPPELIN_JMX_ENABLE")
        .map(System::getenv)
        .map(Boolean::parseBoolean)
        .filter(Boolean::booleanValue)
        .map(jmxEnabled -> "ZEPPELIN_JMX_PORT")
        .map(System::getenv)
        .map(
            portString -> {
              try {
                return Integer.parseInt(portString);
              } catch (Exception e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .forEach(
            port -> {
              try {
                MBeanContainer mbeanContainer =
                    new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
                jettyWebServer.addEventListener(mbeanContainer);
                jettyWebServer.addBean(mbeanContainer);

                JMXServiceURL jmxURL =
                    new JMXServiceURL(
                        String.format(
                            "service:jmx:rmi://0.0.0.0:%d/jndi/rmi://0.0.0.0:%d/jmxrmi",
                            port, port));
                ConnectorServer jmxServer =
                    new ConnectorServer(jmxURL, "org.eclipse.jetty.jmx:name=rmiconnectorserver");
                jettyWebServer.addBean(jmxServer);

                // Add JMX Beans
                // TODO(jl): Need to investigate more about injection and jmx
                jettyWebServer.addBean(
                    sharedServiceLocator.getService(InterpreterSettingManager.class));
                jettyWebServer.addBean(sharedServiceLocator.getService(NotebookServer.class));

                LOG.info("JMX Enabled with port: {}", port);
              } catch (Exception e) {
                LOG.warn("Error while setting JMX", e);
              }
            });

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

    runNoteOnStart(conf);

    Runtime.getRuntime().addShutdownHook(shutdown(conf));

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
      sharedServiceLocator.getService(InterpreterSettingManager.class).close();
    }
  }

  private static Thread shutdown(ZeppelinConfiguration conf) {
    return new Thread(
            () -> {
              LOG.info("Shutting down Zeppelin Server ... ");
              try {
                jettyWebServer.stop();
                if (!conf.isRecoveryEnabled()) {
                  sharedServiceLocator.getService(InterpreterSettingManager.class).close();
                }
                sharedServiceLocator.getService(Notebook.class).close();
                Thread.sleep(3000);
              } catch (Exception e) {
                LOG.error("Error while stopping servlet container", e);
              }
              LOG.info("Bye");
            });
  }

  private static Server setupJettyServer(ZeppelinConfiguration conf) {
    ThreadPool threadPool =
      new QueuedThreadPool(conf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_MAX),
                           conf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_MIN),
                           conf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_TIMEOUT));
    final Server server = new Server(threadPool);
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

  private static void runNoteOnStart(ZeppelinConfiguration conf) throws IOException, InterruptedException {
    String noteIdToRun = conf.getNotebookRunId();
    if (!Strings.isEmpty(noteIdToRun)) {
      NotebookService notebookService = (NotebookService) ServiceLocatorUtilities.getService(
              sharedServiceLocator, NotebookService.class.getName());

      ServiceContext serviceContext;
      String base64EncodedJsonSerializedServiceContext = conf.getNotebookRunServiceContext();
      if (Strings.isEmpty(base64EncodedJsonSerializedServiceContext)) {
        LOG.info("No service context provided. use ANONYMOUS");
        serviceContext = new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<String>() {});
      } else {
        serviceContext = new Gson().fromJson(
                new String(Base64.getDecoder().decode(base64EncodedJsonSerializedServiceContext)),
                ServiceContext.class);
      }

      notebookService.runNote(noteIdToRun, serviceContext, new ServiceCallback<Paragraph>() {
        @Override
        public void onStart(String message, ServiceContext context) throws IOException {
        }

        @Override
        public void onSuccess(Paragraph result, ServiceContext context) throws IOException {
        }

        @Override
        public void onFailure(Exception ex, ServiceContext context) throws IOException {
        }
      });

      if (conf.getNotebookRunAutoShutdown()) {
        Thread t = shutdown(conf);
        t.start();
        t.join();
        System.exit(0);
      }
    }
  }

  private static void configureRequestHeaderSize(
      ZeppelinConfiguration conf, ServerConnector connector) {
    HttpConnectionFactory cf =
        (HttpConnectionFactory) connector.getConnectionFactory(HttpVersion.HTTP_1_1.toString());
    int requestHeaderSize = conf.getJettyRequestHeaderSize();
    cf.getHttpConfiguration().setRequestHeaderSize(requestHeaderSize);
  }

  private static void setupNotebookServer(
      WebAppContext webapp, ZeppelinConfiguration conf, ServiceLocator serviceLocator) {
    String maxTextMessageSize = conf.getWebsocketMaxTextMessageSize();
    final ServletHolder servletHolder =
        new ServletHolder(serviceLocator.getService(NotebookServer.class));
    servletHolder.setInitParameter("maxTextMessageSize", maxTextMessageSize);

    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);

    webapp.addServlet(servletHolder, "/ws/*");
  }

  private static void setupClusterManagerServer(ServiceLocator serviceLocator) {
    InterpreterFactory interpreterFactory
        = sharedServiceLocator.getService(InterpreterFactory.class);
    sharedServiceLocator.getService(ClusterManagerServer.class).start(interpreterFactory);
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
      webapp
          .addFilter(ShiroFilter.class, "/api/*", EnumSet.allOf(DispatcherType.class))
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
}
