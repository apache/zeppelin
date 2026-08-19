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
package org.apache.zeppelin.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.ticket.TicketContainer;
import org.junit.jupiter.api.Test;

class NotebookServerLoggingTest {

  @Test
  void testWebSocketTicketIsNotLoggedOnMessageFailure() {
    String principal = "ticket-log-test-" + UUID.randomUUID();
    TicketContainer.Entry ticketEntry =
        TicketContainer.instance.getTicketEntry(principal, Collections.emptySet());
    String ticket = ticketEntry.getTicket();
    String sensitivePayload = "sensitive-payload-" + UUID.randomUUID();

    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.isAnonymousAllowed()).thenReturn(true);
    NotebookServer notebookServer = new NotebookServer();
    notebookServer.setZeppelinConfiguration(zConf);
    NotebookSocket conn = mock(NotebookSocket.class);
    when(conn.getUser()).thenReturn(principal);
    when(conn.getAuthenticatedIdentity()).thenReturn(
        new AuthenticatedIdentity(principal, Collections.emptySet(), false, null));

    Message message = new Message(OP.CONVERT_NOTE_NBFORMAT)
        .put("ticketCopy", ticket)
        .put("sensitivePayload", sensitivePayload);
    message.principal = principal;
    message.roles = "[]";
    message.ticket = ticket;

    TestAppender appender = new TestAppender();
    org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(NotebookServer.class);
    Level previousLevel = logger.getLevel();
    boolean previousAdditivity = logger.getAdditivity();
    logger.setLevel(Level.TRACE);
    logger.setAdditivity(false);
    logger.addAppender(appender);

    try {
      notebookServer.onMessage(conn, message.toJson());

      assertTrue(appender.hasLevel(Level.ERROR), "The WebSocket error path must be exercised");
      assertTrue(appender.containsMessage("operation=" + OP.CONVERT_NOTE_NBFORMAT));
      assertTrue(appender.containsMessage("principal=" + principal));
      assertFalse(appender.contains(ticket), "WebSocket logs must not contain the ticket");
      assertFalse(appender.contains(sensitivePayload),
          "WebSocket logs must not contain message payload data");
    } finally {
      logger.removeAppender(appender);
      logger.setLevel(previousLevel);
      logger.setAdditivity(previousAdditivity);
      appender.close();
      TicketContainer.instance.removeTicket(principal);
    }
  }

  private static class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(LoggingEvent event) {
      events.add(event);
    }

    boolean hasLevel(Level level) {
      return events.stream().anyMatch(event -> level.equals(event.getLevel()));
    }

    boolean containsMessage(String value) {
      return events.stream()
          .map(LoggingEvent::getRenderedMessage)
          .anyMatch(message -> message != null && message.contains(value));
    }

    boolean contains(String value) {
      for (LoggingEvent event : events) {
        String message = event.getRenderedMessage();
        if (message != null && message.contains(value)) {
          return true;
        }
        String[] throwable = event.getThrowableStrRep();
        if (throwable != null) {
          for (String line : throwable) {
            if (line.contains(value)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
      return false;
    }
  }
}
