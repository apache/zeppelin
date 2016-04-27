package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;


import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinHubRepo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.listener.ZeppelinhubWebsocket;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.SchedulerService;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.scheduler.ZeppelinHubHeartbeat;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.session.ZeppelinhubSession;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Manage a zeppelinhub websocket connection.
 */
public class ZeppelinhubClient {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubClient.class);

  private final WebSocketClient client;
  private final URI zeppelinhubWebsocketUrl;
  private final ClientUpgradeRequest conectionRequest;
  private final String zeppelinhubToken;
  
  private static final int MB = 1048576;
  private static final int MAXIMUN_TEXT_SIZE = 64 * MB;
  private static final long CONNECTION_IDLE_TIME = TimeUnit.SECONDS.toMillis(30);
  
  private SchedulerService schedulerService;
  private ZeppelinhubSession zeppelinhubSession;
  
  public static ZeppelinhubClient newInstance(String url, String token) {
    return new ZeppelinhubClient(url, token);
  }
  
  public void start() {
    try {
      client.start();
      zeppelinhubSession = connect();
      addRoutines();
    } catch (Exception e) {
      LOG.error("Cannot connect to zeppelinhub via websocket", e);
    }
  }
  
  public void stop() {
    LOG.info("Stopping Zeppelinhub websocket client");
    try {
      zeppelinhubSession.close();
      schedulerService.close();
      client.stop();
    } catch (Exception e) {
      LOG.error("Cannot stop zeppelinhub websocket client", e);
    }
  }
  
  public String getToken() {
    return this.zeppelinhubToken;
  }
  
  public void send(String msg) {
    if (!isConnectedToZeppelinhub()) {
      LOG.info("Zeppelinhub connection is not open, opening it");
      zeppelinhubSession = connect();
    }
    zeppelinhubSession.sendByFuture(msg);
  }
  
  private boolean isConnectedToZeppelinhub() {
    return  (zeppelinhubSession == null || !zeppelinhubSession.isSessionOpen()) ? false : true;
  }
  
  private ZeppelinhubClient(String url, String token) {
    zeppelinhubWebsocketUrl = URI.create(url);
    client = createNewWebsocketClient();
    conectionRequest = setConnectionrequest(token);
    zeppelinhubToken = token;
    schedulerService = SchedulerService.create(10);
  }
  
  private ZeppelinhubSession connect() {
    ZeppelinhubSession zeppelinSession;
    try {
      ZeppelinhubWebsocket ws = ZeppelinhubWebsocket.newInstance(zeppelinhubToken);
      Future<Session> future = client.connect(ws, zeppelinhubWebsocketUrl, conectionRequest);
      Session session = future.get();
      zeppelinSession = ZeppelinhubSession.createInstance(session, zeppelinhubToken);
    } catch (IOException | InterruptedException | ExecutionException e) {
      LOG.info("Couldnt connect to zeppelinhub", e);
      zeppelinSession = ZeppelinhubSession.EMPTY;
    }
    return zeppelinSession;
  }
  
  private ClientUpgradeRequest setConnectionrequest(String token) {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setCookies(Lists.newArrayList(new HttpCookie(ZeppelinHubRepo.TOKEN_HEADER, token)));
    return request;
  }
  
  private WebSocketClient createNewWebsocketClient() {
    WebSocketClient client = new WebSocketClient();
    client.setMaxTextMessageBufferSize(MAXIMUN_TEXT_SIZE);
    client.setMaxIdleTimeout(CONNECTION_IDLE_TIME);
    return client;
  }
  
  private void addRoutines() {
    schedulerService.add(ZeppelinHubHeartbeat.newInstance(this), 10, 23);
  }
  
}
