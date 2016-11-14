package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.ZeppelinhubClient;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.mock.MockEchoWebsocketServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeppelinhubClientTest {
  private Logger LOG = LoggerFactory.getLogger(ZeppelinClientTest.class);
  private final int zeppelinPort = 8090;
  private final String validWebsocketUrl = "ws://localhost:" + zeppelinPort + "/ws";
  private ExecutorService executor;
  private MockEchoWebsocketServer echoServer;
  private final String runNotebookMsg =
      "{\"op\":\"RUN_NOTEBOOK\"," +
      "\"data\":[{\"id\":\"20150112-172845_1301897143\",\"title\":null,\"config\":{},\"params\":{},\"data\":null}," +
      "{\"id\":\"20150112-172845_1301897143\",\"title\":null,\"config\":{},\"params\":{},\"data\":null}]," +
      "\"meta\":{\"owner\":\"author\",\"instance\":\"my-zepp\",\"noteId\":\"2AB7SY361\"}}";
  private final String invalidRunNotebookMsg = "some random string";

  @Before
  public void setUp() throws Exception {
    startWebsocketServer();
  }

  @After
  public void tearDown() throws Exception {
    //tear down routine
    echoServer.stop();
    executor.shutdown();
  }

  private void startWebsocketServer() throws InterruptedException {
    // mock zeppelin websocket server setup
    executor = Executors.newFixedThreadPool(1);
    echoServer = new MockEchoWebsocketServer(zeppelinPort);
    executor.submit(echoServer);
  }

  @Test
  public void zeppelinhubClientSingletonTest() {
    ZeppelinhubClient client1 = ZeppelinhubClient.getInstance();
    if (client1 == null) {
      client1 = ZeppelinhubClient.initialize(validWebsocketUrl, "TOKEN");
    } 
    assertNotNull(client1);
    ZeppelinhubClient client2 = ZeppelinhubClient.getInstance();
    assertNotNull(client2);
    assertEquals(client1, client2);
  }

  @Test
  public void runAllParagraphTest() throws Exception {
    Client.initialize(validWebsocketUrl, validWebsocketUrl, "TOKEN", null);
    Client.getInstance().start();
    ZeppelinhubClient zeppelinhubClient = ZeppelinhubClient.getInstance();
    boolean runStatus = zeppelinhubClient.runAllParagraph("2AB7SY361", runNotebookMsg);
    assertTrue(runStatus);
    runStatus = zeppelinhubClient.runAllParagraph("2AB7SY361", invalidRunNotebookMsg);
    assertFalse(runStatus);
    Client.getInstance().stop();
  }

}
