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

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;

/**
 * Unit tests for {@link AnyOfRolesUserAuthorizationFilter}
 * written in Given-When-Then (GWT) style.
 */
class AnyOfRolesUserAuthorizationFilterTest {

    // ---------------------------------------------------------------------------
    // Helper: create a filter whose getSubject(...) returns the supplied subject
    // ---------------------------------------------------------------------------
    private AnyOfRolesUserAuthorizationFilter filterWith(Subject subject) {
        return new AnyOfRolesUserAuthorizationFilter() {
            @Override
            protected Subject getSubject(ServletRequest request, ServletResponse response) {
                return subject;            // inject mock subject
            }
        };
    }

    @Test
    @DisplayName("Given no roles mapped, when isAccessAllowed is called, then it returns true")
    void givenNoRoles_whenIsAccessAllowed_thenTrue() {
        // ----- Given -----
        Subject subject = mock(Subject.class);
        AnyOfRolesUserAuthorizationFilter filter = filterWith(subject);

        // ----- When -----
        boolean allowed = filter.isAccessAllowed(mock(ServletRequest.class),
                mock(ServletResponse.class),
                null /* mappedValue */);

        // ----- Then -----
        assertTrue(allowed);
    }

    @Test
    @DisplayName("Given one matching role, when isAccessAllowed is called, then it returns true")
    void givenSubjectHasRole_whenIsAccessAllowed_thenTrue() {
        // ----- Given -----
        Subject subject = mock(Subject.class);
        when(subject.hasRole("admin")).thenReturn(true);
        AnyOfRolesUserAuthorizationFilter filter = filterWith(subject);

        // ----- When -----
        boolean allowed = filter.isAccessAllowed(mock(ServletRequest.class),
                mock(ServletResponse.class),
                new String[]{"admin", "dev"});

        // ----- Then -----
        assertTrue(allowed);
    }

    @Test
    @DisplayName("Given principal equals one role, when isAccessAllowed is called, " +
            "then it returns true")
    void givenPrincipalMatchesRole_whenIsAccessAllowed_thenTrue() {
        // ----- Given -----
        Subject subject = mock(Subject.class);
        when(subject.hasRole(anyString())).thenReturn(false);
        when(subject.getPrincipal()).thenReturn("data_scientist");
        AnyOfRolesUserAuthorizationFilter filter = filterWith(subject);

        // ----- When -----
        boolean allowed = filter.isAccessAllowed(mock(ServletRequest.class),
                mock(ServletResponse.class),
                new String[]{"guest", "data_scientist"});

        // ----- Then -----
        assertTrue(allowed);
    }

    @Test
    @DisplayName("Given no roles or principal match, when isAccessAllowed is called, " +
            "then it returns false")
    void givenNoMatch_whenIsAccessAllowed_thenFalse() {
        // ----- Given -----
        Subject subject = mock(Subject.class);
        when(subject.hasRole("qa")).thenReturn(false);
        when(subject.hasRole("ops")).thenReturn(false);
        when(subject.getPrincipal()).thenReturn("john_doe");
        AnyOfRolesUserAuthorizationFilter filter = filterWith(subject);

        // ----- When -----
        boolean allowed = filter.isAccessAllowed(mock(ServletRequest.class),
                mock(ServletResponse.class),
                new String[]{"qa", "ops"});

        // ----- Then -----
        assertFalse(allowed);
    }
}
