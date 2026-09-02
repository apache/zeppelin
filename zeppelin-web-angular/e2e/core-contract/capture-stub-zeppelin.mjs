#!/usr/bin/env node
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

import crypto from 'node:crypto';
import http from 'node:http';

const port = Number(process.env.ZEPPELIN_PORT);
const root = process.env.ZEPPELIN_CAPTURE_ROOT;

if (!port || !root) {
  process.stderr.write('ZEPPELIN_PORT and ZEPPELIN_CAPTURE_ROOT are required\n');
  process.exit(2);
}

const server = http.createServer((request, response) => {
  if (request.url === '/api/version') {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end('{"version":"stub"}');
    return;
  }
  response.writeHead(200, { 'content-type': 'application/json' });
  response.end('{"id":"note-a","paragraphs":[{"id":"paragraph-a"}]}');
});

server.on('upgrade', (request, socket) => {
  const key = request.headers['sec-websocket-key'];
  const accept = crypto.createHash('sha1').update(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`).digest('base64');
  socket.write(
    [
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${accept}`,
      '',
      ''
    ].join('\r\n')
  );
  socket.on('data', () => {
    const payload = Buffer.from('{"op":"NOTE"}');
    socket.write(Buffer.concat([Buffer.from([0x81, payload.length]), payload]));
  });
});

server.listen(port, '127.0.0.1');
process.on('SIGTERM', () => server.close(() => process.exit(0)));
