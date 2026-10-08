/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Shared record builders for Node and Playwright tests.

export const fixtureMetadata = () => ({
  coveredOperations: ['GET_NOTE'],
  knownExclusions: [],
  owner: 'zeppelin-web-angular',
  scenario: 'Notebook transport fixture test'
});

export const request = (method, url, body = '', headers = { accept: 'application/json' }) => ({
  headers: () => headers,
  method: () => method,
  postData: () => body,
  url: () => url
});

export const response = (sourceRequest, status, body, headers = { 'content-type': 'application/json' }) => ({
  headers: () => headers,
  request: () => sourceRequest,
  status: () => status,
  text: async () => (typeof body === 'function' ? body() : body)
});

export const wsRecord = (sequence, direction, payloadText) => ({
  kind: 'websocket',
  sequence,
  websocket: { direction, payloadText }
});
