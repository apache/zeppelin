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

import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.PingServlet;
import com.google.gson.Gson;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jetty.InstrumentedQueuedThreadPool;
import io.micrometer.core.instrument.binder.jetty.JettyConnectionMetrics;
import io.micrometer.core.instrument.binder.jetty.JettySslHandshakeMetrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.jetty11.TimedHandler;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.EnumSet;
import jakarta.inject.Singleton;
import javax.management.remote.JMXServiceURL;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.server.ServerEndpointConfig;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.conf.ZeppelinConfiguration.DEFAULT_UI;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.healthcheck.HealthChecks;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.helium.HeliumBundleFactory;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.metric.JVMInfoBinder;
import org.apache.zeppelin.metric.PrometheusServlet;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.scheduler.NoSchedulerService;
import org.apache.zeppelin.notebook.scheduler.QuartzSchedulerService;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.NoSearchService;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.service.*;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.socket.ConnectionManager;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.socket.SessionConfigurator;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.utils.PEMImporter;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.servlet.ServletProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main class of Zeppelin. */
public class ZeppelinServer implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinServer.class);
  private static final String NON_DEFAULT_NEW_UI_WEB_APP_CONTEXT_PATH = "/new";
  private static final String NON_DEFAULT_CLASSIC_UI_WEB_APP_CONTEXT_PATH = "/classic";
  public static final String DEFAULT_SERVICE_LOCATOR_NAME = "shared-locator";

  private final AtomicBoolean duringShutdown = new AtomicBoolean(false);
  private final ZeppelinConfiguration zConf;
  private final Optional<PrometheusMeterRegistry> promMetricRegistry;
  private final Server jettyWebServer;
  private final ServiceLocator sharedServiceLocator;
  private final ConfigStorage storage;

  public ZeppelinServer(ZeppelinConfiguration zConf) throws IOException {
    this(zConf, DEFAULT_SERVICE_LOCATOR_NAME);
  }

  public ZeppelinServer(ZeppelinConfiguration zConf, String serviceLocatorName) throws IOException {
    LOGGER.info("Instantiated ZeppelinServer");
    this.zConf = zConf;
    if (zConf.isPrometheusMetricEnabled()) {
      promMetricRegistry = Optional.of(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    } else {
      promMetricRegistry = Optional.empty();
    }
    jettyWebServer = setupJettyServer();
    sharedServiceLocator = ServiceLocatorFactory.getInstance().create(serviceLocatorName);
    storage = ConfigStorage.createConfigStorage(zConf);
  }

  public void startZeppelin() {
    initMetrics();

    TimedHandler timedHandler = new TimedHandler(Metrics.globalRegistry, Tags.empty());
    jettyWebServer.setHandler(timedHandler);

    ContextHandlerCollection contexts = new ContextHandlerCollection();
    timedHandler.setHandler(contexts);
    ServiceLocatorUtilities.enableImmediateScope(sharedServiceLocator);
    ServiceLocatorUtilities.addClasses(sharedServiceLocator,
      ImmediateErrorHandlerImpl.class);
    ImmediateErrorHandlerImpl handler = sharedServiceLocator.getService(ImmediateErrorHandlerImpl.class);


    ServiceLocatorUtilities.bind(
        sharedServiceLocator,
        new AbstractBinder() {
          @Override
          protected void configure() {
            bind(storage).to(ConfigStorage.class);
            bindAsContract(PluginManager.class).in(Singleton.class);
            bind(GsonNoteParser.class).to(NoteParser.class).in(Singleton.class);
            bindAsContract(InterpreterFactory.class).in(Singleton.class);
            bindAsContract(NotebookRepoSync.class).to(NotebookRepo.class).in(Singleton.class);
            bindAsContract(Helium.class).in(Singleton.class);
            bind(zConf).to(ZeppelinConfiguration.class);
            bindAsContract(InterpreterSettingManager.class).in(Singleton.class);
            bindAsContract(InterpreterService.class).in(Singleton.class);
            bindAsContract(Credentials.class).in(Singleton.class);
            bindAsContract(AdminService.class).in(Singleton.class);
            bindAsContract(AuthorizationService.class).in(Singleton.class);
            bindAsContract(ConnectionManager.class).in(Singleton.class);
            bindAsContract(NoteManager.class).in(Singleton.class);
            // TODO(jl): Will make it more beautiful
            if (!StringUtils.isBlank(zConf.getShiroPath())) {
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
            bindAsContract(NotebookServer.class)
                .to(AngularObjectRegistryListener.class)
                .to(RemoteInterpreterProcessListener.class)
                .to(ApplicationEventListener.class)
                .to(NoteEventListener.class)
                .in(Singleton.class);
            if (zConf.isZeppelinNotebookCronEnable()) {
              bind(QuartzSchedulerService.class).to(SchedulerService.class).in(Singleton.class);
            } else {
              bind(NoSchedulerService.class).to(SchedulerService.class).in(Singleton.class);
            }
            if (zConf.getBoolean(ConfVars.ZEPPELIN_SEARCH_ENABLE)) {
              bind(LuceneSearch.class).to(SearchService.class).in(Singleton.class);
            } else {
              bind(NoSearchService.class).to(SearchService.class).in(Singleton.class);
            }
          }
        });

    // Multiple Web UI
    String classicUiWebAppContextPath;
    String newUiWebAppContextPath;
    if (isNewUiDefault(zConf)) {
      classicUiWebAppContextPath = NON_DEFAULT_CLASSIC_UI_WEB_APP_CONTEXT_PATH;
      newUiWebAppContextPath = zConf.getServerContextPath();
    } else {
      classicUiWebAppContextPath = zConf.getServerContextPath();
      newUiWebAppContextPath = NON_DEFAULT_NEW_UI_WEB_APP_CONTEXT_PATH;
    }
    final WebAppContext newUiWebApp = setupWebAppContext(contexts, zConf, zConf.getString(ConfVars.ZEPPELIN_ANGULAR_WAR),
        newUiWebAppContextPath);
    final WebAppContext classicUiWebApp = setupWebAppContext(contexts, zConf, zConf.getString(ConfVars.ZEPPELIN_WAR),
        classicUiWebAppContextPath);

    initWebApp(newUiWebApp);
    initWebApp(classicUiWebApp);

    NotebookRepo repo =
        ServiceLocatorUtilities.getService(sharedServiceLocator, NotebookRepo.class.getName());
    NoteParser noteParser =
        ServiceLocatorUtilities.getService(sharedServiceLocator, NoteParser.class.getName());
    try {
      repo.init(zConf, noteParser);
    } catch (IOException e) {
      LOGGER.error("Failed to init NotebookRepo", e);
    }

    initJMX();

    runNoteOnStart(sharedServiceLocator);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    // Try to get Notebook from ServiceLocator, because Notebook instantiation is lazy, it is
    // created when user open zeppelin in browser if we don't get it explicitly here.
    // Lazy loading will cause paragraph recovery and cron job initialization is delayed.
    Notebook notebook = ServiceLocatorUtilities.getService(
            sharedServiceLocator, Notebook.class.getName());
    ServiceLocatorUtilities.getService(
      sharedServiceLocator, SearchService.class.getName());
    ServiceLocatorUtilities.getService(
      sharedServiceLocator, SchedulerService.class.getName());
    // Initialization of the Notes in the notebook asynchronously
    notebook.initNotebook();
    // Try to recover here, don't do it in constructor of Notebook, because it would cause deadlock.
    notebook.recoveryIfNecessary();

    LOGGER.info("Starting zeppelin server");
    /*
     * Get a nice Dump after jetty start, quite helpful for debugging
     * jettyWebServer.setDumpAfterStart(true);
     */
    try {
      jettyWebServer.start(); // Instantiates ZeppelinServer
      if (zConf.getJettyName() != null) {
        org.eclipse.jetty.http.HttpGenerator.setJettyVersion(zConf.getJettyName());
      }
    } catch (Exception e) {
      LOGGER.error("Error while running jettyServer", e);
      System.exit(-1);
    }

    LOGGER.info("Done, zeppelin server started");
    try {
      List<ErrorData> errorDatas = handler.waitForAtLeastOneConstructionError(5000);
      for (ErrorData errorData : errorDatas) {
        LOGGER.error("Error in Construction", errorData.getThrowable());
      }
      if (!errorDatas.isEmpty()) {
        LOGGER.error("{} error(s) while starting - Termination", errorDatas.size());
        System.exit(-1);
      }
    } catch (InterruptedException e) {
      // Many fast unit tests interrupt the Zeppelin server at this point
      LOGGER.error("Interrupt while waiting for construction errors - init shutdown", e);
      shutdown();
      Thread.currentThread().interrupt();
    }

    if (jettyWebServer.isStopped() || jettyWebServer.isStopping()) {
      LOGGER.debug("jetty server is stopped {} - is stopping {}", jettyWebServer.isStopped(), jettyWebServer.isStopping());
    } else {
      try {
        jettyWebServer.join();
      } catch (InterruptedException e) {
        LOGGER.error("Interrupt while waiting for jetty threads - init shutdown", e);
        shutdown();
        Thread.currentThread().interrupt();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.printShortInfo();
    try (ZeppelinServer server = new ZeppelinServer(zConf)) {
      server.startZeppelin();
    }
  }

  private void initJMX() {
    // JMX Enable
    if (zConf.isJMXEnabled()) {
      int port = zConf.getJMXPort();
      // Setup JMX
      MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
      jettyWebServer.addBean(mbeanContainer);
      JMXServiceURL jmxURL;
      try {
        jmxURL = new JMXServiceURL(
            String.format(
                "service:jmx:rmi://0.0.0.0:%d/jndi/rmi://0.0.0.0:%d/jmxrmi",
                port, port));
        ConnectorServer jmxServer = new ConnectorServer(jmxURL, "org.eclipse.jetty.jmx:name=rmiconnectorserver");
        jettyWebServer.addBean(jmxServer);
        LOGGER.info("JMX Enabled with port: {}", port);
      } catch (MalformedURLException e) {
        LOGGER.error("Invalid JMXServiceURL - JMX Disabled", e);
      }
    }
  }
  private void initMetrics() {
    if (zConf.isJMXEnabled()) {
      Metrics.addRegistry(new JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM));
    }
    if (promMetricRegistry.isPresent()) {
      Metrics.addRegistry(promMetricRegistry.get());
    }
    new ClassLoaderMetrics().bindTo(Metrics.globalRegistry);
    new JvmMemoryMetrics().bindTo(Metrics.globalRegistry);
    new JvmThreadMetrics().bindTo(Metrics.globalRegistry);
    new FileDescriptorMetrics().bindTo(Metrics.globalRegistry);
    new ProcessorMetrics().bindTo(Metrics.globalRegistry);
    new UptimeMetrics().bindTo(Metrics.globalRegistry);
    new JVMInfoBinder().bindTo(Metrics.globalRegistry);
  }

  public void shutdown(int exitCode) {
    if (!duringShutdown.getAndSet(true)) {
      LOGGER.info("Shutting down Zeppelin Server ... - ExitCode {}", exitCode);
      try {
        if (jettyWebServer != null) {
          jettyWebServer.stop();
        }
        if (sharedServiceLocator != null) {
          if (!zConf.isRecoveryEnabled()) {
            sharedServiceLocator.getService(InterpreterSettingManager.class).close();
          }
          sharedServiceLocator.getService(Notebook.class).close();
        }
      } catch (Exception e) {
        LOGGER.error("Error while stopping servlet container", e);
      }
      LOGGER.info("Bye");
      if (exitCode != 0) {
        System.exit(exitCode);
      }
    }
  }

  public void shutdown() {
    shutdown(0);
  }

  private Server setupJettyServer() {
    InstrumentedQueuedThreadPool threadPool =
      new InstrumentedQueuedThreadPool(Metrics.globalRegistry, Tags.empty(),
                           zConf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_MAX),
                           zConf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_MIN),
                           zConf.getInt(ConfVars.ZEPPELIN_SERVER_JETTY_THREAD_POOL_TIMEOUT));
    final Server server = new Server(threadPool);
    initServerConnector(server);
    return server;
  }
  private void initServerConnector(Server server) {

    ServerConnector connector;
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.addCustomizer(new ForwardedRequestCustomizer());
    httpConfig.setSendServerVersion(zConf.sendJettyName());
    httpConfig.setRequestHeaderSize(zConf.getJettyRequestHeaderSize());
    if (zConf.useSsl()) {
      LOGGER.debug("Enabling SSL for Zeppelin Server on port {}", zConf.getServerSslPort());
      httpConfig.setSecureScheme(HttpScheme.HTTPS.asString());
      httpConfig.setSecurePort(zConf.getServerSslPort());

      HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
      httpsConfig.addCustomizer(new SecureRequestCustomizer());

      SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(getSslContextFactory(zConf), HttpVersion.HTTP_1_1.asString());
      HttpConnectionFactory httpsConnectionFactory = new HttpConnectionFactory(httpsConfig);
      connector =
              new ServerConnector(
                      server,
                      sslConnectionFactory,
                      httpsConnectionFactory);
      connector.setPort(zConf.getServerSslPort());
      connector.addBean(new JettySslHandshakeMetrics(Metrics.globalRegistry, Tags.empty()));
    } else {
      HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);
      connector =
              new ServerConnector(
                      server,
                      httpConnectionFactory);
      connector.setPort(zConf.getServerPort());
    }
    // Set some timeout options to make debugging easier.
    int timeout = 1000 * 30;
    connector.setIdleTimeout(timeout);
    connector.setHost(zConf.getServerAddress());
    connector.addBean(new JettyConnectionMetrics(Metrics.globalRegistry, Tags.empty()));
    server.addConnector(connector);
  }

  private void runNoteOnStart(ServiceLocator sharedServiceLocator) {
    String noteIdToRun = zConf.getNotebookRunId();
    if (!StringUtils.isEmpty(noteIdToRun)) {
      LOGGER.info("Running note {} on start", noteIdToRun);
      NotebookService notebookService = ServiceLocatorUtilities.getService(
              sharedServiceLocator, NotebookService.class.getName());

      ServiceContext serviceContext;
      String base64EncodedJsonSerializedServiceContext = zConf.getNotebookRunServiceContext();
      if (StringUtils.isEmpty(base64EncodedJsonSerializedServiceContext)) {
        LOGGER.info("No service context provided. use ANONYMOUS");
        serviceContext = new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());
      } else {
        serviceContext = new Gson().fromJson(
                new String(Base64.getDecoder().decode(base64EncodedJsonSerializedServiceContext)),
                ServiceContext.class);
      }

      try {
        boolean success = notebookService.runAllParagraphs(noteIdToRun, null, serviceContext, new ServiceCallback<Paragraph>() {
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
        if (zConf.getNotebookRunAutoShutdown()) {
          shutdown(success ? 0 : 1);
        }
      } catch (IOException e) {
        LOGGER.error("Error during Paragraph Execution", e);
      }
    }
  }

  private void setupNotebookServer(WebAppContext webapp) {
    String maxTextMessageSize = zConf.getWebsocketMaxTextMessageSize();
    JakartaWebSocketServletContainerInitializer
            .configure(webapp, (servletContext, wsContainer) -> {
              wsContainer.setDefaultMaxTextMessageBufferSize(Integer.parseInt(maxTextMessageSize));
              wsContainer.addEndpoint(ServerEndpointConfig.Builder.create(NotebookServer.class, "/ws")
              .configurator(new SessionConfigurator(sharedServiceLocator)).build());
            });
  }

  private static SslContextFactory.Server getSslContextFactory(ZeppelinConfiguration zConf) {
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

    // initialize KeyStore
    // Check for PEM files
    if (StringUtils.isNoneBlank(zConf.getPemKeyFile(), zConf.getPemCertFile())) {
      setupKeystoreWithPemFiles(sslContextFactory, zConf);
    } else {
      // Set keystore
      sslContextFactory.setKeyStorePath(zConf.getKeyStorePath());
      sslContextFactory.setKeyStoreType(zConf.getKeyStoreType());
      sslContextFactory.setKeyStorePassword(zConf.getKeyStorePassword());
      sslContextFactory.setKeyManagerPassword(zConf.getKeyManagerPassword());
    }

    // initialize TrustStore
    if (zConf.useClientAuth()) {
      if (StringUtils.isNotBlank(zConf.getPemCAFile())) {
        setupTruststoreWithPemFiles(sslContextFactory, zConf);
      } else {
        sslContextFactory.setNeedClientAuth(zConf.useClientAuth());
        // Set truststore
        sslContextFactory.setTrustStorePath(zConf.getTrustStorePath());
        sslContextFactory.setTrustStoreType(zConf.getTrustStoreType());
        sslContextFactory.setTrustStorePassword(zConf.getTrustStorePassword());
      }
    }

    return sslContextFactory;
  }

  private static void setupKeystoreWithPemFiles(SslContextFactory.Server sslContextFactory, ZeppelinConfiguration zConf) {
    File pemKey = new File(zConf.getPemKeyFile());
    File pemCert = new File(zConf.getPemCertFile());
    boolean isPemKeyFileReadable = Files.isReadable(pemKey.toPath());
    boolean isPemCertFileReadable = Files.isReadable(pemCert.toPath());
    if (!isPemKeyFileReadable) {
      LOGGER.warn("PEM key file {} is not readable", pemKey);
    }
    if (!isPemCertFileReadable) {
      LOGGER.warn("PEM cert file {} is not readable", pemCert);
    }
    if (isPemKeyFileReadable && isPemCertFileReadable) {
      try {
        String password = zConf.getPemKeyPassword();
        sslContextFactory.setKeyStore(PEMImporter.loadKeyStore(pemCert, pemKey, password));
        sslContextFactory.setKeyStoreType("JKS");
        sslContextFactory.setKeyStorePassword(password);
      } catch (IOException | GeneralSecurityException e) {
        LOGGER.error("Failed to initialize KeyStore from PEM files", e);
      }
    } else {
      LOGGER.error("Failed to read PEM files");
    }
  }

  private static void setupTruststoreWithPemFiles(SslContextFactory.Server sslContextFactory, ZeppelinConfiguration zConf) {
    File pemCa = new File(zConf.getPemCAFile());
    if (Files.isReadable(pemCa.toPath())) {
      try {
        sslContextFactory.setTrustStore(PEMImporter.loadTrustStore(pemCa));
        sslContextFactory.setTrustStoreType("JKS");
        sslContextFactory.setTrustStorePassword("");
        sslContextFactory.setNeedClientAuth(zConf.useClientAuth());
      } catch (IOException | GeneralSecurityException e) {
        LOGGER.error("Failed to initialize TrustStore from PEM CA file", e);
      }
    } else {
      LOGGER.error("PEM CA file {} is not readable", pemCa);
    }
  }

  private void setupRestApiContextHandler(WebAppContext webapp) {
    final ServletHolder servletHolder =
        new ServletHolder(new org.glassfish.jersey.servlet.ServletContainer());

    servletHolder.setInitParameter("jakarta.ws.rs.Application", RestApiApplication.class.getName());
    servletHolder.setName("rest");
    webapp.addServlet(servletHolder, "/api/*");

    String shiroIniPath = zConf.getShiroPath();
    if (!StringUtils.isBlank(shiroIniPath)) {
      webapp.setInitParameter("shiroConfigLocations", new File(shiroIniPath).toURI().toString());
      webapp
          .addFilter(ShiroFilter.class, "/api/*", EnumSet.allOf(DispatcherType.class))
          .setInitParameter("staticSecurityManagerEnabled", "true");
      webapp.addEventListener(new EnvironmentLoaderListener());
    }
  }

  private void setupPrometheusContextHandler(WebAppContext webapp) {
    if (promMetricRegistry.isPresent()) {
      webapp.addServlet(new ServletHolder(new PrometheusServlet(promMetricRegistry.get())), "/metrics");
    }
  }

  private static void setupHealthCheckContextHandler(WebAppContext webapp) {
    webapp.addServlet(new ServletHolder(new HealthCheckServlet(HealthChecks.getHealthCheckReadinessRegistry())), "/health/readiness");
    webapp.addServlet(new ServletHolder(new HealthCheckServlet(HealthChecks.getHealthCheckLivenessRegistry())), "/health/liveness");
    webapp.addServlet(new ServletHolder(new PingServlet()), "/ping");
  }

  private static WebAppContext setupWebAppContext(
      ContextHandlerCollection contexts, ZeppelinConfiguration zConf, String warPath, String contextPath) {
    WebAppContext webApp = new WebAppContext();
    webApp.setContextPath(contextPath);
    LOGGER.info("warPath is: {}", warPath);
    File warFile = new File(warPath);
    if (warFile.isDirectory()) {
      // Development mode, read from FS
      // webApp.setDescriptor(warPath+"/WEB-INF/web.xml");
      webApp.setResourceBase(warFile.getPath());
    } else {
      // use packaged WAR
      webApp.setWar(warFile.getAbsolutePath());
      webApp.setExtractWAR(false);
      File warTempDirectory = new File(zConf.getAbsoluteDir(ConfVars.ZEPPELIN_WAR_TEMPDIR) + contextPath);
      warTempDirectory.mkdir();
      LOGGER.info("ZeppelinServer Webapp path: {}", warTempDirectory.getPath());
      webApp.setTempDirectory(warTempDirectory);
    }
    // Explicit bind to root
    webApp.addServlet(new ServletHolder(new IndexHtmlServlet(zConf, contextPath)), "/index.html");
    contexts.addHandler(webApp);

    webApp.addFilter(new FilterHolder(new CorsFilter(zConf)), "/*", EnumSet.allOf(DispatcherType.class));

    webApp.setInitParameter(
        "org.eclipse.jetty.servlet.Default.dirAllowed",
        Boolean.toString(zConf.getBoolean(ConfVars.ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED)));
    return webApp;
  }

  private void initWebApp(WebAppContext webApp) {
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
    setupRestApiContextHandler(webApp);

    // prometheus endpoint
    setupPrometheusContextHandler(webApp);
    // health endpoints
    setupHealthCheckContextHandler(webApp);

    // Notebook server
    setupNotebookServer(webApp);
  }

  private static boolean isNewUiDefault(ZeppelinConfiguration zConf) {
    return zConf.getDefaultUi() == DEFAULT_UI.NEW;
  }

  @Override
  public void close() throws Exception {
    shutdown();
  }
}
