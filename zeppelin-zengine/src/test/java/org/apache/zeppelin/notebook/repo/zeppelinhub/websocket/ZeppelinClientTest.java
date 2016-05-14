package org.apache.zeppelin.notebook.repo.zeppelinhub.websocket;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.mock.MockEchoWebsocketServer;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class ZeppelinClientTest {
  private Logger LOG = LoggerFactory.getLogger(ZeppelinClientTest.class);
  private final int zeppelinPort = 8080;
  private final String validWebsocketUrl = "ws://localhost:" + zeppelinPort + "/ws";
  private ExecutorService executor;
  private MockEchoWebsocketServer echoServer;

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
  public void zeppelinConnectionTest() {
    try {
      // Wait for websocket server to start
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      LOG.warn("Cannot wait for websocket server to start, returning");
      return;
    }
    // Initialize and start Zeppelin client
    ZeppelinClient client = ZeppelinClient.initialize(validWebsocketUrl, "dummy token", null);
    client.start();
    LOG.info("Zeppelin websocket client started");

    // Connection to note AAAA
    Session connectionA = client.getZeppelinConnection("AAAA");
    assertNotNull(connectionA);
    assertTrue(connectionA.isOpen());

    assertEquals(client.countConnectedNotes(), 1);
    assertEquals(connectionA, client.getZeppelinConnection("AAAA"));

    // Connection to note BBBB
    Session connectionB = client.getZeppelinConnection("BBBB");
    assertNotNull(connectionB);
    assertTrue(connectionB.isOpen());

    assertEquals(client.countConnectedNotes(), 2);
    assertEquals(connectionB, client.getZeppelinConnection("BBBB"));

    // Remove connection to note AAAA
    client.removeZeppelinConnection("AAAA");
    assertEquals(client.countConnectedNotes(), 1);
    assertNotEquals(connectionA, client.getZeppelinConnection("AAAA"));
    assertEquals(client.countConnectedNotes(), 2);
    client.stop();
  }

  @Test
  public void zeppelinClientSingletonTest() {
    ZeppelinClient client1 = ZeppelinClient.getInstance();
    if (client1 == null) {
      client1 = ZeppelinClient.initialize(validWebsocketUrl, "TOKEN", null);
    }
    assertNotNull(client1);
    ZeppelinClient client2 = ZeppelinClient.getInstance();
    assertNotNull(client2);
    assertEquals(client1, client2);
  }

  @Test
  public void zeppelinMessageSerializationTest() {
    Message msg = new Message(OP.LIST_NOTES);
    msg.data = Maps.newHashMap();
    msg.data.put("key", "value");
    ZeppelinClient client = ZeppelinClient.initialize(validWebsocketUrl, "TOKEN", null);
    String serializedMsg = client.serialize(msg);
    Message deserializedMsg = client.deserialize(serializedMsg);
    assertEquals(msg.op, deserializedMsg.op);
    assertEquals(msg.data.get("key"), deserializedMsg.data.get("key"));
    
    String invalidMsg = "random text";
    deserializedMsg =client.deserialize(invalidMsg);
    assertNull(deserializedMsg);
  }

  @Test
  public void sendToZeppelinTest() {
    ZeppelinClient client = ZeppelinClient.initialize(validWebsocketUrl, "TOKEN", null);
    client.start();
    Message msg = new Message(OP.LIST_NOTES);
    msg.data = Maps.newHashMap();
    msg.data.put("key", "value");
    client.send(msg, "DDDD");
    client.removeZeppelinConnection("DDDD");
    client.stop();
  }
}
