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
package org.apache.zeppelin.rest.exception;

import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.zeppelin.utils.ExceptionUtils;

/**
 * UnauthorizedException handler for WebApplicationException.
 * 
 */
public class UnauthorizedException extends WebApplicationException {
  private static final long serialVersionUID = 4394749068760407567L;
  private static final String UNAUTHORIZED_MSG = "Authorization required";

  public UnauthorizedException() {
    super(unauthorizedJson(UNAUTHORIZED_MSG));
  }

  private static Response unauthorizedJson(String message) {
    return ExceptionUtils.jsonResponseContent(FORBIDDEN, message);
  }
  
  public UnauthorizedException(Throwable cause, String message) {
    super(cause, unauthorizedJson(message));
  }
  
  public UnauthorizedException(String message) {
    super(unauthorizedJson(message));
  }

}
