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

package org.apache.zeppelin.utils;

import jakarta.websocket.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ServerUtils#getRemoteAddress(Session)} written in
 * Given-When-Then style.
 */
class ServerUtilsTest {

    private static final String KEY = "jakarta.websocket.endpoint.remoteAddress";

    @Test
    @DisplayName("Given a session with remoteAddress, when getRemoteAddress is called, " +
            "then it returns the address")
    void givenSessionWithAddress_whenGetRemoteAddress_thenReturnAddress() {
        // ----- Given -----
        Session session = mock(Session.class);
        Map<String, Object> props = new HashMap<>();
        props.put(KEY, "127.0.0.1");
        when(session.getUserProperties()).thenReturn(props);

        // ----- When -----
        String actual = ServerUtils.getRemoteAddress(session);

        // ----- Then -----
        assertEquals("127.0.0.1", actual);
    }

    @Test
    @DisplayName("Given a session without remoteAddress, when getRemoteAddress is called, " +
            "then it returns \"null\"")
    void givenSessionWithoutAddress_whenGetRemoteAddress_thenReturnNullString() {
        // ----- Given -----
        Session session = mock(Session.class);
        when(session.getUserProperties()).thenReturn(new HashMap<>());

        // ----- When -----
        String actual = ServerUtils.getRemoteAddress(session);

        // ----- Then -----
        assertEquals("null", actual);   // expects the literal string "null"
    }

    @Test
    @DisplayName("Given a null session, when getRemoteAddress is called, " +
            "then it returns \"unknown\"")
    void givenNullSession_whenGetRemoteAddress_thenReturnUnknown() {
        // ----- Given -----
        Session session = null;

        // ----- When -----
        String actual = ServerUtils.getRemoteAddress(session);

        // ----- Then -----
        assertEquals("unknown", actual);
    }
}
