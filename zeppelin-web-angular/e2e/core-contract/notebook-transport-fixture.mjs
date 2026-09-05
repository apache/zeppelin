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

import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export const fixtureVersion = 1;

export const restDirections = new Set(['request', 'response']);
export const websocketDirections = new Set(['send', 'receive']);

const safeHeaderNames = new Set(['accept', 'content-type']);
const volatileFieldNames = new Set(['dateCreated', 'dateFinished', 'dateStarted', 'lastUpdated', 'msgId', 'time']);
const sensitiveFieldNames = new Set([
  'authorization',
  'api-key',
  'apikey',
  'client-secret',
  'clientsecret',
  'cookie',
  'credential',
  'credentials',
  'password',
  'principal',
  'secret',
  'set-cookie',
  'ticket',
  'token'
]);

export function normalizeFixtureRecord(value) {
  if (Array.isArray(value)) {
    return value.map(item => normalizeFixtureRecord(item));
  }
  if (!value || typeof value !== 'object') {
    return value;
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, entry]) => [
      key,
      shouldRedactField(key) ? `<${key}>` : volatileFieldNames.has(key) ? `<${key}>` : normalizeFixtureRecord(entry)
    ])
  );
}

export function normalizeFixture(fixture) {
  return {
    ...fixture,
    records: fixture.records.map(record => normalizeFixtureRecord(record))
  };
}

export function sanitizeFixture(fixture) {
  return normalizeFixture({
    ...fixture,
    records: fixture.records.map(record => sanitizeRecord(record))
  });
}

export function validateFixture(fixture) {
  const errors = validateReplayFixture(fixture);
  if (!fixture || typeof fixture !== 'object') {
    return errors;
  }
  validateFixtureMetadata(errors, fixture.metadata);
  return errors;
}

export function validateReplayFixture(fixture) {
  const errors = [];
  if (!fixture || typeof fixture !== 'object') {
    return ['fixture must be an object'];
  }
  if (fixture.version !== fixtureVersion) {
    errors.push(`Unsupported fixture version ${fixture.version}`);
  }
  if (!Array.isArray(fixture.records) || fixture.records.length === 0) {
    errors.push('Fixture records must be a non-empty array');
    return errors;
  }

  let previousSequence = 0;
  for (const [index, record] of fixture.records.entries()) {
    const prefix = `records[${index}]`;
    if (!record || typeof record !== 'object' || Array.isArray(record)) {
      errors.push(`${prefix} must be an object`);
      continue;
    }
    if (!Number.isInteger(record.sequence) || record.sequence <= previousSequence) {
      errors.push(`${prefix}.sequence must increase without reordering`);
    }
    previousSequence = record.sequence;

    if (record.kind === 'rest') {
      validateRestRecord(errors, prefix, record);
    } else if (record.kind === 'websocket') {
      validateWebSocketRecord(errors, prefix, record);
    } else {
      errors.push(`${prefix}.kind must be rest or websocket`);
    }
  }
  return errors;
}

export function visitNormalizedFixtureRecords(fixture, visitor = () => undefined) {
  const adapter = createFixtureReplayAdapter(fixture);
  for (const record of adapter.records()) {
    visitor(record);
  }
}

export const replayFixture = visitNormalizedFixtureRecords;

export function createFixtureReplayAdapter(fixture) {
  const errors = validateReplayFixture(fixture);
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  const records = sanitizeFixture(fixture).records;
  const replayRecords = records.filter(
    record => record.kind === 'websocket' || (record.kind === 'rest' && record.rest.direction === 'response')
  );
  let replayCursor = 0;

  const nextRecord = () => replayRecords[replayCursor];
  const consumeServerMessages = () => {
    const messages = [];
    while (nextRecord()?.kind === 'websocket' && nextRecord().websocket.direction === 'receive') {
      messages.push(deserializeWebSocketPayload(nextRecord().websocket));
      replayCursor += 1;
    }
    return messages;
  };

  return {
    records: () => records[Symbol.iterator](),
    route: async (route, request) => {
      if (!isNotebookRestUrl(request.url())) {
        await route.continue?.();
        return;
      }
      const method = request.method();
      const requestKey = `${method} ${urlPath(request.url())}`;
      const response = nextRecord();
      if (!response || response.kind !== 'rest') {
        throw new Error(
          `Transport fixture out of order: expected ${describeReplayRecord(response)}, got REST ${requestKey}`
        );
      }
      const responseKey = `${response.rest.request.method} ${response.rest.request.url}`;
      if (responseKey !== requestKey) {
        throw new Error(`REST fixture request out of order: expected ${responseKey}, got ${requestKey}`);
      }
      assertRestRequestMatches(response.rest.request, summarizeRequest(request), requestKey);
      replayCursor += 1;
      await route.fulfill({
        body: serializeRestBody(response.rest),
        contentType: response.rest.headers['content-type'] ?? 'application/json',
        headers: response.rest.headers,
        status: response.rest.status
      });
    },
    receiveWebSocketMessages: consumeServerMessages,
    sendWebSocketMessage: message => {
      const expected = nextRecord();
      if (!expected) {
        throw new Error(
          `WebSocket fixture messages exhausted before client send: ${stringifyWebSocketMessage(message)}`
        );
      }
      if (expected.kind !== 'websocket' || expected.websocket.direction !== 'send') {
        throw new Error(
          `Transport fixture out of order: expected ${describeReplayRecord(expected)}, got WebSocket send`
        );
      }
      const expectedPayload = deserializeWebSocketPayload(expected.websocket);
      if (!webSocketPayloadMatches(expectedPayload, message)) {
        throw new Error(
          `WebSocket fixture send mismatch: expected ${expectedPayload}, got ${stringifyWebSocketMessage(message)}`
        );
      }
      replayCursor += 1;
    },
    assertComplete: () => {
      if (replayCursor !== replayRecords.length) {
        throw new Error(`Transport fixture has ${replayRecords.length - replayCursor} unconsumed record(s)`);
      }
    },
    hasWebSocketRecords: () => replayRecords.some(record => record.kind === 'websocket')
  };
}

export function createPlaywrightFixtureAdapter(fixture, options = {}) {
  const errors = validateReplayFixture(fixture);
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  const records = sanitizeFixture(fixture).records;
  const pendingRestRequests = [];
  let cursor = 0;
  let webSocket;
  let draining = false;
  let drainRequested = false;

  const nextRecord = () => records[cursor];
  const drain = async () => {
    if (draining) {
      drainRequested = true;
      return;
    }

    draining = true;
    try {
      while (true) {
        const record = nextRecord();
        if (!record) {
          return;
        }
        if (record.kind === 'websocket') {
          if (record.websocket.direction === 'receive' && webSocket) {
            webSocket.send(deserializeWebSocketPayload(record.websocket));
            cursor += 1;
            continue;
          }
          return;
        }

        if (record.rest.direction === 'request') {
          const pending = pendingRestRequests.find(entry => !entry.requestMatched);
          if (!pending) {
            return;
          }
          const requestKey = `${pending.request.method()} ${urlPath(pending.request.url())}`;
          assertRestRequestMatches(record.rest.request, summarizeRequest(pending.request), requestKey);
          pending.requestMatched = true;
          cursor += 1;
          continue;
        }

        const pending = pendingRestRequests.find(entry => entry.requestMatched);
        if (!pending) {
          return;
        }
        const responseKey = `${record.rest.request.method} ${record.rest.request.url}`;
        const requestKey = `${pending.request.method()} ${urlPath(pending.request.url())}`;
        if (responseKey !== requestKey) {
          throw new Error(`REST fixture request out of order: expected ${responseKey}, got ${requestKey}`);
        }
        pendingRestRequests.splice(pendingRestRequests.indexOf(pending), 1);
        cursor += 1;
        try {
          await pending.route.fulfill({
            body: serializeRestBody(record.rest),
            contentType: record.rest.headers['content-type'] ?? 'application/json',
            headers: record.rest.headers,
            status: record.rest.status
          });
          pending.resolve();
        } catch (error) {
          pending.reject(error);
          throw error;
        }
      }
    } finally {
      draining = false;
      if (drainRequested) {
        drainRequested = false;
        void drain();
      }
    }
  };

  return {
    install: async page => {
      await page.route('**/api/**', async (route, request) => {
        if (!isNotebookRestUrl(request.url())) {
          await route.continue?.();
          return;
        }
        let resolve;
        let reject;
        const completed = new Promise((resolvePromise, rejectPromise) => {
          resolve = resolvePromise;
          reject = rejectPromise;
        });
        const pending = { reject, request, requestMatched: false, resolve, route };
        pendingRestRequests.push(pending);
        try {
          await drain();
          const next = nextRecord();
          if (!pending.requestMatched && next?.kind === 'websocket' && next.websocket.direction === 'send') {
            pendingRestRequests.splice(pendingRestRequests.indexOf(pending), 1);
            throw new Error(
              `Transport fixture out of order: expected WebSocket send, got REST ${pending.request.method()} ${urlPath(
                pending.request.url()
              )}`
            );
          }
          await completed;
        } catch (error) {
          const pendingIndex = pendingRestRequests.indexOf(pending);
          if (pendingIndex !== -1) {
            pendingRestRequests.splice(pendingIndex, 1);
          }
          throw error;
        }
      });
      await page.routeWebSocket(/\/ws(?:$|\?)/, ws => {
        if (webSocket) {
          throw new Error('Transport fixture supports one WebSocket connection per fixture');
        }
        webSocket = ws;
        const server = options.passthrough && typeof ws.connectToServer === 'function' ? ws.connectToServer() : null;
        void drain();
        ws.onMessage(message => {
          const record = nextRecord();
          if (!record) {
            throw new Error(
              `WebSocket fixture messages exhausted before client send: ${stringifyWebSocketMessage(message)}`
            );
          }
          if (record.kind !== 'websocket' || record.websocket.direction !== 'send') {
            throw new Error(
              `Transport fixture out of order: expected ${describeReplayRecord(record)}, got WebSocket send`
            );
          }
          const expectedPayload = deserializeWebSocketPayload(record.websocket);
          if (!webSocketPayloadMatches(expectedPayload, message)) {
            throw new Error(
              `WebSocket fixture send mismatch: expected ${expectedPayload}, got ${stringifyWebSocketMessage(message)}`
            );
          }
          cursor += 1;
          if (server) {
            server.send(message);
          }
          void drain();
        });
      });
    },
    assertComplete: () => {
      if (records.some(record => record.kind === 'websocket') && !webSocket) {
        throw new Error('WebSocket fixture was never connected');
      }
      if (pendingRestRequests.length > 0 || cursor !== records.length) {
        throw new Error(`Transport fixture has ${records.length - cursor} unconsumed record(s)`);
      }
    }
  };
}

export function createNotebookTransportRecorder(metadata) {
  const metadataErrors = [];
  validateFixtureMetadata(metadataErrors, metadata);
  if (metadataErrors.length > 0) {
    throw new Error(metadataErrors.join('\n'));
  }

  const records = [];
  const pending = new Set();
  let sequence = 0;

  const record = value => {
    const entry = {
      ...value,
      sequence: ++sequence
    };
    records.push(entry);
    return entry;
  };

  return {
    install: page => {
      page.on('request', request => {
        if (!isNotebookRestUrl(request.url())) {
          return;
        }
        record({
          kind: 'rest',
          rest: {
            direction: 'request',
            request: summarizeRequest(request)
          }
        });
      });
      page.on('response', async response => {
        const request = response.request();
        if (!isNotebookRestUrl(request.url())) {
          return;
        }
        const entry = record({
          kind: 'rest',
          rest: {
            direction: 'response',
            headers: filterHeaders(response.headers()),
            request: summarizeRequest(request),
            status: response.status(),
            bodyRaw: ''
          }
        });
        const bodyRead = safeResponseText(response)
          .then(body => {
            Object.assign(entry.rest, parseRestBody(body, response.headers()));
          })
          .finally(() => pending.delete(bodyRead));
        pending.add(bodyRead);
      });
      page.on('websocket', socket => {
        if (!isNotebookWebSocketUrl(socket.url())) {
          return;
        }
        socket.on('framesent', frame => recordCapturedWebSocketFrame(record, 'send', framePayload(frame)));
        socket.on('framereceived', frame => recordCapturedWebSocketFrame(record, 'receive', framePayload(frame)));
      });
    },
    stop: async () => {
      await Promise.all([...pending]);
    },
    snapshot: () => sanitizeFixture({ metadata, records: [...records], version: fixtureVersion }),
    write: async fixturePath => {
      await Promise.all([...pending]);
      const sanitized = sanitizeFixture({ metadata, records: [...records], version: fixtureVersion });
      mkdirSync(path.dirname(fixturePath), { recursive: true });
      writeFileSync(fixturePath, `${JSON.stringify(sanitized, null, 2)}\n`);
      return sanitized;
    }
  };
}

export function serializeRestBody(rest) {
  if ('bodyJson' in rest) {
    return JSON.stringify(rest.bodyJson);
  }
  return rest.bodyRaw ?? rest.body ?? '';
}

export function parseRestBody(body, headers = {}) {
  const contentType = headers['content-type'] ?? headers['Content-Type'] ?? '';
  const trimmed = body.trim();
  if (!trimmed) {
    return { bodyRaw: '' };
  }
  if (contentType.includes('application/json') || /^[{[]/.test(trimmed)) {
    try {
      return { bodyJson: JSON.parse(body) };
    } catch {
      return { bodyRaw: redactRawSensitiveValues(body) };
    }
  }
  return { bodyRaw: redactRawSensitiveValues(body) };
}

export function stringifyWebSocketMessage(message) {
  return Buffer.isBuffer(message) ? message.toString('utf8') : String(message);
}

export function webSocketPayloadMatches(expectedPayload, actualMessage) {
  const expectedBinary = toBinaryBuffer(expectedPayload);
  const actualBinary = toBinaryBuffer(actualMessage);
  if (expectedBinary || actualBinary) {
    return Boolean(expectedBinary && actualBinary && expectedBinary.equals(actualBinary));
  }

  const actualPayload = stringifyWebSocketMessage(actualMessage);
  if (looksLikeJson(expectedPayload) && looksLikeJson(actualPayload)) {
    try {
      return (
        stableJson(normalizeFixtureRecord(JSON.parse(actualPayload))) ===
        stableJson(normalizeFixtureRecord(JSON.parse(expectedPayload)))
      );
    } catch {
      return false;
    }
  }
  return expectedPayload === actualPayload;
}

function summarizeRequest(request) {
  const body = request.postData() ?? '';
  return {
    headers: filterHeaders(request.headers()),
    method: request.method(),
    url: urlPath(request.url()),
    ...parseRestBody(body, request.headers())
  };
}

function sanitizeRecord(record) {
  if (record.kind === 'websocket') {
    return {
      ...record,
      websocket: sanitizeWebSocket(record.websocket)
    };
  }
  if (record.kind !== 'rest') {
    return record;
  }
  return {
    ...record,
    rest: {
      ...record.rest,
      ...(record.rest.headers ? { headers: filterHeaders(record.rest.headers) } : {}),
      ...(record.rest.request
        ? {
            request: {
              ...record.rest.request,
              headers: filterHeaders(record.rest.request.headers)
            }
          }
        : {})
    }
  };
}

function filterHeaders(headers = {}) {
  return Object.fromEntries(
    Object.entries(headers)
      .map(([key, value]) => [key.toLowerCase(), Array.isArray(value) ? value.join(', ') : String(value ?? '')])
      .filter(([key]) => safeHeaderNames.has(key))
  );
}

function webSocketRecord(direction, payload) {
  return {
    kind: 'websocket',
    websocket: {
      direction,
      ...(Buffer.isBuffer(payload) ? { payloadBase64: payload.toString('base64') } : { payloadText: String(payload) })
    }
  };
}

function recordCapturedWebSocketFrame(record, direction, payload) {
  if (toBinaryBuffer(payload)) {
    throw new Error('Binary WebSocket frames cannot be captured until a binary redaction policy is defined');
  }
  record(webSocketRecord(direction, payload));
}

export function isNotebookRestUrl(value) {
  const url = new URL(value);
  return url.pathname === '/api/notebook' || url.pathname.startsWith('/api/notebook/');
}

function isNotebookWebSocketUrl(value) {
  const url = new URL(value);
  return url.pathname === '/ws';
}

async function safeResponseText(response) {
  return response.text();
}

function urlPath(value) {
  const url = new URL(value);
  for (const [key] of url.searchParams) {
    if (shouldRedactField(key)) {
      url.searchParams.set(key, `<${key}>`);
    } else if (volatileFieldNames.has(key)) {
      url.searchParams.set(key, `<${key}>`);
    }
  }
  return `${url.pathname}${url.search}`;
}

function stableJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(entry => stableJson(entry)).join(',')}]`;
  }
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function sanitizeWebSocket(websocket) {
  if (!websocket?.payloadText || !looksLikeJson(websocket.payloadText)) {
    return websocket;
  }
  try {
    return {
      ...websocket,
      payloadText: JSON.stringify(normalizeFixtureRecord(JSON.parse(websocket.payloadText)))
    };
  } catch {
    return { ...websocket, payloadText: redactRawSensitiveValues(websocket.payloadText) };
  }
}

function deserializeWebSocketPayload(websocket) {
  if ('payloadBase64' in websocket) {
    return Buffer.from(websocket.payloadBase64, 'base64');
  }
  return websocket.payloadText ?? '';
}

function framePayload(frame) {
  if (frame && typeof frame === 'object' && 'payload' in frame) {
    return frame.payload;
  }
  return frame;
}

function assertRestRequestMatches(expectedRequest, actualRequest, requestKey) {
  const expected = normalizeFixtureRecord(sanitizeRestRequest(expectedRequest));
  const actual = normalizeFixtureRecord(sanitizeRestRequest(actualRequest));
  if (stableJson(expected) !== stableJson(actual)) {
    throw new Error(
      `REST fixture request mismatch for ${requestKey}: expected ${stableJson(expected)}, got ${stableJson(actual)}`
    );
  }
}

function sanitizeRestRequest(request) {
  return {
    ...request,
    headers: filterHeaders(request.headers)
  };
}

function shouldRedactField(key) {
  const normalized = key.toLowerCase();
  return (
    sensitiveFieldNames.has(normalized) ||
    normalized.includes('apikey') ||
    normalized.includes('credential') ||
    normalized.includes('password') ||
    normalized.includes('secret') ||
    normalized.includes('token')
  );
}

function redactRawSensitiveValues(value) {
  const sensitiveFieldPattern = '(?:api[-_]?key|client[-_]?secret|credential(?:s)?|password|secret|ticket|token)';
  return String(value)
    .replace(new RegExp(`([?&]${sensitiveFieldPattern}=)([^&#\\s]+)`, 'gi'), (_match, prefix) => `${prefix}<redacted>`)
    .replace(
      new RegExp(`((?:[\\"']?${sensitiveFieldPattern}[\\"']?\\s*[:=]\\s*))(?:[\\"']?)([^,}\\]\\s\\"']*)`, 'gi'),
      (_match, prefix) => `${prefix}<redacted>`
    );
}

function describeReplayRecord(record) {
  if (!record) {
    return 'end of fixture';
  }
  if (record.kind === 'rest') {
    return `REST ${record.rest.request.method} ${record.rest.request.url}`;
  }
  return `WebSocket ${record.websocket.direction}`;
}

function toBinaryBuffer(value) {
  if (Buffer.isBuffer(value)) {
    return value;
  }
  if (value instanceof ArrayBuffer) {
    return Buffer.from(value);
  }
  if (ArrayBuffer.isView(value)) {
    return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
  }
  return null;
}

const validateRestRecord = (errors, prefix, record) => {
  if (!record.rest || typeof record.rest !== 'object') {
    errors.push(`${prefix}.rest is required`);
    return;
  }
  if (!restDirections.has(record.rest.direction)) {
    errors.push(`${prefix}.rest.direction must be request or response`);
  }
  if (!isHttpMethod(record.rest.request?.method)) {
    errors.push(`${prefix}.rest.request.method is required`);
  }
  if (typeof record.rest.request?.url !== 'string') {
    errors.push(`${prefix}.rest.request.url is required`);
  }
  if (!isHeaderRecord(record.rest.request?.headers)) {
    errors.push(`${prefix}.rest.request.headers must be an object`);
  }
  if (record.rest.direction === 'response') {
    if (!Number.isInteger(record.rest.status)) {
      errors.push(`${prefix}.rest.status is required for responses`);
    }
    if (!isHeaderRecord(record.rest.headers)) {
      errors.push(`${prefix}.rest.headers must be an object`);
    }
    if (!hasRestBody(record.rest)) {
      errors.push(`${prefix}.rest.bodyJson or bodyRaw is required to preserve response shape`);
    }
  } else if (!hasRestBody(record.rest.request)) {
    errors.push(`${prefix}.rest.request.bodyJson or bodyRaw is required to preserve request shape`);
  }
};

const validateFixtureMetadata = (errors, metadata) => {
  if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
    errors.push('metadata must be an object');
    return;
  }
  if (typeof metadata.scenario !== 'string' || !metadata.scenario.trim()) {
    errors.push('metadata.scenario must be a non-empty string');
  }
  if (typeof metadata.owner !== 'string' || !metadata.owner.trim()) {
    errors.push('metadata.owner must be a non-empty string');
  }
  if (
    !Array.isArray(metadata.coveredOperations) ||
    metadata.coveredOperations.length === 0 ||
    metadata.coveredOperations.some(operation => typeof operation !== 'string' || !operation.trim())
  ) {
    errors.push('metadata.coveredOperations must be a non-empty string array');
  }
  if (
    !Array.isArray(metadata.knownExclusions) ||
    metadata.knownExclusions.some(exclusion => typeof exclusion !== 'string' || !exclusion.trim())
  ) {
    errors.push('metadata.knownExclusions must be a string array');
  }
};

const validateWebSocketRecord = (errors, prefix, record) => {
  if (!record.websocket || typeof record.websocket !== 'object') {
    errors.push(`${prefix}.websocket is required`);
    return;
  }
  if (!websocketDirections.has(record.websocket.direction)) {
    errors.push(`${prefix}.websocket.direction must be send or receive`);
  }
  if (!('payloadText' in record.websocket) && !('payloadBase64' in record.websocket)) {
    errors.push(`${prefix}.websocket payloadText or payloadBase64 is required to preserve message shape`);
  }
};

const isHttpMethod = value => typeof value === 'string' && /^[A-Z]+$/.test(value);

const isHeaderRecord = value =>
  Boolean(value) &&
  typeof value === 'object' &&
  !Array.isArray(value) &&
  Object.values(value).every(entry => typeof entry === 'string');

const hasRestBody = value => Boolean(value) && ('bodyJson' in value || 'bodyRaw' in value || 'body' in value);

const looksLikeJson = value => /^[{[]/.test(String(value).trim());
