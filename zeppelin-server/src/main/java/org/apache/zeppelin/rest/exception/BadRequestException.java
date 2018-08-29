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

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.apache.zeppelin.utils.ExceptionUtils;

/** BadRequestException handler for WebApplicationException. */
public class BadRequestException extends WebApplicationException {
  public BadRequestException() {
    super(ExceptionUtils.jsonResponse(BAD_REQUEST));
  }

  private static Response badRequestJson(String message) {
    return ExceptionUtils.jsonResponseContent(BAD_REQUEST, message);
  }

  public BadRequestException(Throwable cause, String message) {
    super(cause, badRequestJson(message));
  }

  public BadRequestException(String message) {
    super(badRequestJson(message));
  }
}
