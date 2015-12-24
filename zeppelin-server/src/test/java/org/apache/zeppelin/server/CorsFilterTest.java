/**
 * Created by joelz on 8/6/15.
 *
 *
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

import org.apache.zeppelin.socket.TestHttpServletRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * BASIC Cors rest api tests
 */
public class CorsFilterTest {

    public static String[] headers = new String[8];
    public static Integer count = 0;

    @Test
    public void ValidCorsFilterTest() throws IOException, ServletException {
        CorsFilter filter = new CorsFilter();
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        FilterChain mockedFilterChain = mock(FilterChain.class);
        TestHttpServletRequest mockRequest = mock(TestHttpServletRequest.class);
        when(mockRequest.getHeader("Origin")).thenReturn("http://localhost:8080");
        when(mockRequest.getMethod()).thenReturn("Empty");
        when(mockRequest.getServerName()).thenReturn("localhost");
        count = 0;

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                headers[count] = invocationOnMock.getArguments()[1].toString();
                count++;
                return null;
            }
        }).when(mockResponse).addHeader(anyString(), anyString());

        filter.doFilter(mockRequest, mockResponse, mockedFilterChain);
        Assert.assertTrue(headers[0].equals("http://localhost:8080"));
    }

    @Test
    public void InvalidCorsFilterTest() throws IOException, ServletException {
        CorsFilter filter = new CorsFilter();
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        FilterChain mockedFilterChain = mock(FilterChain.class);
        TestHttpServletRequest mockRequest = mock(TestHttpServletRequest.class);
        when(mockRequest.getHeader("Origin")).thenReturn("http://evillocalhost:8080");
        when(mockRequest.getMethod()).thenReturn("Empty");
        when(mockRequest.getServerName()).thenReturn("evillocalhost");

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                headers[count] = invocationOnMock.getArguments()[1].toString();
                count++;
                return null;
            }
        }).when(mockResponse).addHeader(anyString(), anyString());

        filter.doFilter(mockRequest, mockResponse, mockedFilterChain);
        Assert.assertTrue(headers[0].equals(""));
    }
}
