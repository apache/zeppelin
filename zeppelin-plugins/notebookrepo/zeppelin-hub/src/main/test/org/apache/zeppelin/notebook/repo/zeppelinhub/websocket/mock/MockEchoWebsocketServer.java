package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.mock;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.LoggerFactory;

public class MockEchoWebsocketServer implements Runnable {
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MockEchoWebsocketServer.class);
  private Server server;

  public MockEchoWebsocketServer(int port) {
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(port);
    server.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);

    //ServletHolder holderEvents = new ServletHolder("ws-events", MockEventServlet.class);
    context.addServlet(MockEventServlet.class, "/ws/*");
  }

  public void start() throws Exception {
    LOG.info("Starting mock echo websocket server");
    server.start();
    server.join();
  }

  public void stop() throws Exception {
    LOG.info("Stopping mock echo websocket server");
    server.stop();
  }

  @Override
  public void run() {
    try {
      this.start();
    } catch (Exception e) {
      LOG.error("Couldn't start mock echo websocket server", e);
    }
  }

}
