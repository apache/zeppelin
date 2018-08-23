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

package org.apache.zeppelin.service;

import java.io.IOException;

/** This will be used by service classes as callback mechanism. */
public interface ServiceCallback<T> {

  /**
   * Called when this service call is starting
   *
   * @param message
   * @param context
   * @throws IOException
   */
  void onStart(String message, ServiceContext context) throws IOException;

  /**
   * Called when this service call is succeed
   *
   * @param result
   * @param context
   * @throws IOException
   */
  void onSuccess(T result, ServiceContext context) throws IOException;

  /**
   * Called when this service call is failed
   *
   * @param ex
   * @param context
   * @throws IOException
   */
  void onFailure(Exception ex, ServiceContext context) throws IOException;
}
