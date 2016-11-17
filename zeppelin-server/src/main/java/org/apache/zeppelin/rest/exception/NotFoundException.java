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
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.zeppelin.utils.ExceptionUtils;

/**
 * Not Found handler for WebApplicationException.
 * 
 */
public class NotFoundException extends WebApplicationException {
  private static final long serialVersionUID = 2459398393216512293L;

  /**
   * Create a HTTP 404 (Not Found) exception.
   */
  public NotFoundException() {
    super(ExceptionUtils.jsonResponse(NOT_FOUND));
  }

  /**
   * Create a HTTP 404 (Not Found) exception.
   * @param message the String that is the entity of the 404 response.
   */
  public NotFoundException(String message) {
    super(notFoundJson(message));
  }

  private static Response notFoundJson(String message) {
    return ExceptionUtils.jsonResponseContent(NOT_FOUND, message);
  }

  public NotFoundException(Throwable cause) {
    super(cause, notFoundJson(cause.getMessage()));
  }

  public NotFoundException(Throwable cause, String message) {
    super(cause, notFoundJson(message));
  }

}
