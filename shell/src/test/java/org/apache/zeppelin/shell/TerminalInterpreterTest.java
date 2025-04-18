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

package org.apache.zeppelin.shell;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ClientEndpointConfig.Builder;
import jakarta.websocket.ClientEndpointConfig.Configurator;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.shell.terminal.TerminalSocketTest;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;
import java.util.regex.Pattern;

class TerminalInterpreterTest extends BaseInterpreterTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalInterpreterTest.class);

  private TerminalInterpreter terminal;
  private InterpreterContext intpContext;
  private InterpreterResult result;

  @Override
  @BeforeEach
  public void setUp() throws InterpreterException {
    Properties p = new Properties();
    intpContext = getIntpContext();

    terminal = new TerminalInterpreter(p);
    terminal.open();

    if (System.getProperty("os.name").startsWith("Windows")) {
      result = terminal.interpret("dir", intpContext);
    } else {
      result = terminal.interpret("ls", intpContext);
    }
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @Override
  @AfterEach
  public void tearDown() throws InterpreterException {
    terminal.close();
  }

  @Test
  void testInvalidCommand() {
    Session session = null;
    WebSocketContainer webSocketContainer = null;

    try {
      // mock connect terminal
      boolean running = terminal.terminalThreadIsRunning();
      assertTrue(running);

      URI webSocketConnectionUri = URI.create("ws://" + terminal.getTerminalHostIp() +
          ":" + terminal.getTerminalPort() + "/terminal/");
      LOGGER.info("webSocketConnectionUri: " + webSocketConnectionUri);
      String origin = "http://" + terminal.getTerminalHostIp() + ":" + terminal.getTerminalPort();
      LOGGER.info("origin: " + origin);
      ClientEndpointConfig clientEndpointConfig = getOriginRequestHeaderConfig(origin);
      webSocketContainer = ContainerProvider.getWebSocketContainer();

      // Attempt Connect
      session = webSocketContainer.connectToServer(
          TerminalSocketTest.class, clientEndpointConfig, webSocketConnectionUri);

      // Send Start terminal service message
      String terminalReadyCmd = String.format("{\"type\":\"TERMINAL_READY\"," +
          "\"noteId\":\"noteId-1\",\"paragraphId\":\"paragraphId-1\"}");
      LOGGER.info("send > " + terminalReadyCmd);
      session.getBasicRemote().sendText(terminalReadyCmd);
      Thread.sleep(10000);

      LOGGER.info(TerminalSocketTest.ReceivedMsg.toString());
      String msg = TerminalSocketTest.ReceivedMsg.get(0);
      LOGGER.info(msg);
      // {"text":"\u001b[?1034hbash-3.2$ \r\u001b[Kbash-3.2$
      // \r\u001b[Kbash-3.2$ ","type":"TERMINAL_PRINT"}
      String pattern = "\\{\"text\":\".*\"type\":\"TERMINAL_PRINT\"}";
      boolean isMatch = Pattern.matches(pattern, msg);
      assertTrue(isMatch);

      // Send invalid_command message
      String echoHelloWorldCmd = String.format("{\"type\":\"TERMINAL_COMMAND\"," +
          "\"command\":\"invalid_command\r\"}");
      LOGGER.info("send > " + echoHelloWorldCmd);
      session.getBasicRemote().sendText(echoHelloWorldCmd);
      Thread.sleep(5000);

      LOGGER.info(TerminalSocketTest.ReceivedMsg.toString());
      // [{"text":"invalid_co \rmmand\r\n","type":"TERMINAL_PRINT"},
      //  {"text":"bash: invalid_command: command not found\r\n","type":"TERMINAL_PRINT"},
      //  {"text":"bash-3.2$ ","type":"TERMINAL_PRINT"}]
      boolean return_invalid_command = false;
      for (String msg2 : TerminalSocketTest.ReceivedMsg) {
        boolean find = msg2.contains("invalid_command: command not found");
        if (find) {
          LOGGER.info("find return terminal print: " + msg2);
          return_invalid_command = true;
          break;
        }
      }
      assertTrue(return_invalid_command);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (DeploymentException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      try {
        session.close();
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }

      // Force lifecycle stop when done with container.
      // This is to free up threads and resources that the
      // JSR-356 container allocates. But unfortunately
      // the JSR-356 spec does not handle lifecycles (yet)
      if (webSocketContainer instanceof LifeCycle) {
        try {
          ((LifeCycle) webSocketContainer).stop();
        } catch (Exception e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }
  }

  @Test
  void testValidCommand() {
    Session session = null;
    WebSocketContainer webSocketContainer = null;

    try {
      // mock connect terminal
      boolean running = terminal.terminalThreadIsRunning();
      assertTrue(running);

      URI webSocketConnectionUri = URI.create("ws://" + terminal.getTerminalHostIp() +
          ":" + terminal.getTerminalPort() + "/terminal/");
      LOGGER.info("webSocketConnectionUri: " + webSocketConnectionUri);
      String origin = "http://" + terminal.getTerminalHostIp() + ":" + terminal.getTerminalPort();
      LOGGER.info("origin: " + origin);
      ClientEndpointConfig clientEndpointConfig = getOriginRequestHeaderConfig(origin);
      webSocketContainer = ContainerProvider.getWebSocketContainer();

      // Attempt Connect
      session = webSocketContainer.connectToServer(
          TerminalSocketTest.class, clientEndpointConfig, webSocketConnectionUri);

      // Send Start terminal service message
      String terminalReadyCmd = String.format("{\"type\":\"TERMINAL_READY\"," +
          "\"noteId\":\"noteId-1\",\"paragraphId\":\"paragraphId-1\"}");
      LOGGER.info("send > " + terminalReadyCmd);
      session.getBasicRemote().sendText(terminalReadyCmd);
      Thread.sleep(10000);

      LOGGER.info(TerminalSocketTest.ReceivedMsg.toString());
      String msg = TerminalSocketTest.ReceivedMsg.get(0);
      LOGGER.info(msg);
      // {"text":"\u001b[?1034hbash-3.2$ \r\u001b[Kbash-3.2$
      // \r\u001b[Kbash-3.2$ ","type":"TERMINAL_PRINT"}
      String pattern = "\\{\"text\":\".*\"type\":\"TERMINAL_PRINT\"}";
      boolean isMatch = Pattern.matches(pattern, msg);
      assertTrue(isMatch);

      // Send echo 'hello world!' message
      String echoHelloWorldCmd = String.format("{\"type\":\"TERMINAL_COMMAND\"," +
          "\"command\":\"echo 'hello world!'\r\"}");
      LOGGER.info("send > " + echoHelloWorldCmd);
      session.getBasicRemote().sendText(echoHelloWorldCmd);
      Thread.sleep(5000);

      // [{"text":"echo \u0027hell \ro world!\u0027\r\n","type":"TERMINAL_PRINT"},
      //  {"text":"hello world!\r\nbash-3.2$ ","type":"TERMINAL_PRINT"}]
      LOGGER.info(TerminalSocketTest.ReceivedMsg.toString());
      boolean return_hello_world = false;
      for (String msg2 : TerminalSocketTest.ReceivedMsg) {
        boolean find = msg2.contains("hello world!");
        if (find) {
          LOGGER.info("find return terminal print: " + msg2);
          return_hello_world = true;
          break;
        }
      }
      assertTrue(return_hello_world);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (DeploymentException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      try {
        session.close();
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }

      // Force lifecycle stop when done with container.
      // This is to free up threads and resources that the
      // JSR-356 container allocates. But unfortunately
      // the JSR-356 spec does not handle lifecycles (yet)
      if (webSocketContainer instanceof LifeCycle) {
        try {
          ((LifeCycle) webSocketContainer).stop();
        } catch (Exception e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }
  }

  @Test
  void testValidOrigin() {
    Session session = null;

    // mock connect terminal
    boolean running = terminal.terminalThreadIsRunning();
    assertTrue(running);

    URI webSocketConnectionUri = URI.create("ws://" + terminal.getTerminalHostIp() +
        ":" + terminal.getTerminalPort() + "/terminal/");
    LOGGER.info("webSocketConnectionUri: " + webSocketConnectionUri);
    String origin = "http://" + terminal.getTerminalHostIp() + ":" + terminal.getTerminalPort();
    LOGGER.info("origin: " + origin);
    ClientEndpointConfig clientEndpointConfig = getOriginRequestHeaderConfig(origin);
    WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

    Throwable exception = null;
    try {
      // Attempt Connect
      session = webSocketContainer.connectToServer(
          TerminalSocketTest.class, clientEndpointConfig, webSocketConnectionUri);
    } catch (DeploymentException e) {
      exception = e;
    } catch (IOException e) {
      exception = e;
    } finally {
      if (session != null) {
        try {
          session.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }

      // Force lifecycle stop when done with container.
      // This is to free up threads and resources that the
      // JSR-356 container allocates. But unfortunately
      // the JSR-356 spec does not handle lifecycles (yet)
      if (webSocketContainer instanceof LifeCycle) {
        try {
          ((LifeCycle) webSocketContainer).stop();
        } catch (Exception e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    assertNull(exception);
  }

  @Test
  void testInvalidOrigin() {
    Session session = null;

    // mock connect terminal
    boolean running = terminal.terminalThreadIsRunning();
    assertTrue(running);

    URI webSocketConnectionUri = URI.create("ws://" + terminal.getTerminalHostIp() +
        ":" + terminal.getTerminalPort() + "/terminal/");
    LOGGER.info("webSocketConnectionUri: " + webSocketConnectionUri);
    String origin = "http://invalid-origin";
    LOGGER.info("origin: " + origin);
    ClientEndpointConfig clientEndpointConfig = getOriginRequestHeaderConfig(origin);
    WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

    Throwable exception = null;
    try {
      // Attempt Connect
      session = webSocketContainer.connectToServer(
          TerminalSocketTest.class, clientEndpointConfig, webSocketConnectionUri);
    } catch (DeploymentException e) {
      exception = e;
    } catch (IOException e) {
      exception = e;
    } finally {
      if (session != null) {
        try {
          session.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }

      // Force lifecycle stop when done with container.
      // This is to free up threads and resources that the
      // JSR-356 container allocates. But unfortunately
      // the JSR-356 spec does not handle lifecycles (yet)
      if (webSocketContainer instanceof LifeCycle) {
        try {
          ((LifeCycle) webSocketContainer).stop();
        } catch (Exception e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    assertTrue(exception instanceof IOException);
    assertTrue(exception.getMessage().contains("403 Forbidden"));
  }

  private static ClientEndpointConfig getOriginRequestHeaderConfig(String origin) {
    Configurator configurator = new Configurator() {
      @Override
      public void beforeRequest(Map<String, List<String>> headers) {
        headers.put("Origin", Arrays.asList(origin));
      }
    };
    ClientEndpointConfig clientEndpointConfig = Builder.create()
        .configurator(configurator)
        .build();
    return clientEndpointConfig;
  }
}
