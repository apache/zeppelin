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

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExceptionUtils} written in Given-When-Then style.
 */
class ExceptionUtilsTest {

    @Test
    @DisplayName("Given a status, when jsonResponse is called, then the response has that status")
    void givenStatus_whenJsonResponse_thenStatusCodeMatches() {
        // ----- Given -----
        Status expectedStatus = Status.BAD_REQUEST;

        // ----- When -----
        Response response = ExceptionUtils.jsonResponse(expectedStatus);

        // ----- Then -----
        assertEquals(expectedStatus.getStatusCode(), response.getStatus());

        // entity is a JsonResponse<?>; convert to JSON string via toString()
        String json = response.getEntity().toString();
        assertTrue(json.contains("\"status\":\"" + expectedStatus.name() + "\""),
                "JSON should contain the enum name in \"status\"");
    }

    @Test
    @DisplayName("Given a status and message, when jsonResponseContent is called, " +
            "then JSON contains both")
    void givenStatusAndMessage_whenJsonResponseContent_thenJsonContainsBoth() {
        // ----- Given -----
        Status expectedStatus = Status.NOT_FOUND;
        String expectedMessage = "Resource not found";

        // ----- When -----
        Response response = ExceptionUtils.jsonResponseContent(expectedStatus, expectedMessage);

        // ----- Then -----
        assertEquals(expectedStatus.getStatusCode(), response.getStatus());

        String json = response.getEntity().toString();
        assertTrue(json.contains("\"status\":\"" + expectedStatus.name() + "\""),
                "JSON should contain the enum name in \"status\"");
        assertTrue(json.contains(expectedMessage),
                "JSON should contain the custom message");
    }
}