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

const restDirections = new Set(['request', 'response']);
const websocketDirections = new Set(['send', 'receive']);

const safeHeaderNames = new Set(['accept', 'content-type']);
const volatileFieldNames = new Set([
  'dateCreated',
  'dateFinished',
  'dateStarted',
  'dateUpdated',
  'lastUpdated',
  'msgId',
  'time'
]);
// Mask participant identities and permission sets by field name.
// Do not text-scan these names: `user=` can be ordinary notebook content.
const principalFieldNames = new Set(['owners', 'readers', 'roles', 'runners', 'user', 'users', 'writers']);

// Redact credential containers and identity objects whole.
// Mask permission-array entries individually to preserve their count.
// Recurse into volatile containers: a form named `time` may contain notebook data.
const isContainer = value => value !== null && typeof value === 'object';

const maskFieldValue = (key, value) => {
  if (!isContainer(value)) {
    return `<${key}>`;
  }
  if (shouldRedactField(key)) {
    return `<${key}>`;
  }
  if (principalFieldNames.has(key)) {
    return Array.isArray(value) ? value.map(() => `<${key}>`) : `<${key}>`;
  }
  return normalizeFixtureRecord(value);
};

// Text-scan opaque URLs and raw bodies for credentials.
// sanitizeWebSocket handles payloadText separately to preserve structured notebook content.
const textScannedFieldNames = new Set(['bodyRaw', 'url']);

export function normalizeFixtureRecord(value) {
  if (Array.isArray(value)) {
    // Preserve array strings as notebook content; sensitive parent fields are masked before recursion.
    return value.map(item => (typeof item === 'string' ? item : normalizeFixtureRecord(item)));
  }
  // Opaque payloads need text scanning because they have no field names.
  if (typeof value === 'string') {
    return redactEmbeddedSecrets(value);
  }
  if (!value || typeof value !== 'object') {
    return value;
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, entry]) => {
      if (shouldRedactField(key) || volatileFieldNames.has(key) || principalFieldNames.has(key)) {
        return [key, maskFieldValue(key, entry)];
      }
      // Preserve keyed notebook text such as `ticketCount = 5`.
      // Only the opaque fields above need text scanning in addition to field redaction.
      if (typeof entry === 'string') {
        return [key, textScannedFieldNames.has(key) ? redactEmbeddedSecrets(entry) : entry];
      }
      return [key, normalizeFixtureRecord(entry)];
    })
  );
}

function normalizeFixture(fixture) {
  return {
    ...fixture,
    records: fixture.records.map(record => normalizeFixtureRecord(record))
  };
}

export function sanitizeFixture(fixture) {
  const ids = new Map();
  return normalizeFixture({
    ...fixture,
    records: fixture.records.map(record => {
      const sanitized = sanitizeRecord(record);
      const envelope = parseEnvelope(sanitized.websocket?.payloadText);
      if (typeof envelope?.msgId === 'string' && envelope.msgId !== '<msgId>') {
        if (!ids.has(envelope.msgId)) {
          ids.set(envelope.msgId, `<msgId:${ids.size + 1}>`);
        }
        sanitized.websocket.payloadText = JSON.stringify({ ...envelope, msgId: ids.get(envelope.msgId) });
      }
      return sanitized;
    })
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
  const unanswered = [];
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
      if (record.rest?.request) {
        const key = stableJson(normalizeFixtureRecord(record.rest.request));
        if (record.rest.direction === 'request') {
          unanswered.push({ key, prefix });
        } else if (record.rest.direction === 'response') {
          const match = unanswered.findIndex(entry => entry.key === key);
          if (match !== -1) {
            unanswered.splice(match, 1);
          }
        }
      }
    } else if (record.kind === 'websocket') {
      validateWebSocketRecord(errors, prefix, record);
      const envelope = parseEnvelope(record.websocket?.payloadText);
      if (envelope && Object.hasOwn(envelope, 'msgId') && envelope.msgId !== null) {
        if (envelope.msgId === '<msgId>') {
          errors.push(`${prefix} has ambiguous erased msgId; recapture this fixture`);
        } else if (typeof envelope.msgId !== 'string' || !envelope.msgId) {
          errors.push(`${prefix} envelope msgId must be a non-empty string or null`);
        }
      }
    } else {
      errors.push(`${prefix}.kind must be rest or websocket`);
    }
  }
  for (const entry of unanswered) {
    errors.push(`${entry.prefix} request has no response; finish or recapture the operation`);
  }
  return errors;
}

export function createPlaywrightFixtureAdapter(fixture) {
  const errors = validateReplayFixture(fixture);
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }

  const records = sanitizeFixture(fixture).records;
  const pendingRestRequests = [];
  const inFlightRestDeliveries = new Set();
  const recordedMessageIds = new Set(
    records.map(record => parseEnvelope(record.websocket?.payloadText)?.msgId).filter(id => typeof id === 'string')
  );
  const messageIds = new Map();
  const runtimeMessageIds = new Map();
  let cursor = 0;
  let webSocket;
  let draining = false;
  let drainRequested = false;
  let fatalError;
  let deliveryError;

  const nextRecord = () => records[cursor];
  // Normalize live and recorded URLs identically.
  const pendingKey = entry => `${entry.request.method()} ${normalizeFixtureRecord(urlPath(entry.request.url()))}`;
  const removePendingRestRequest = entry => {
    const index = pendingRestRequests.indexOf(entry);
    if (index !== -1) {
      pendingRestRequests.splice(index, 1);
    }
  };
  // Reject requests with no remaining response instead of waiting indefinitely.
  const hasRemainingResponseFor = key =>
    records
      .slice(cursor)
      .some(
        record =>
          record.kind === 'rest' &&
          record.rest.direction === 'response' &&
          `${record.rest.request.method} ${record.rest.request.url}` === key
      );
  const failPendingRestRequests = error => {
    fatalError ??= error;
    for (const entry of pendingRestRequests.splice(0, pendingRestRequests.length)) {
      entry.reject(error);
    }
  };
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
            const payload = deserializeWebSocketPayload(record.websocket);
            const envelope = parseEnvelope(payload);
            webSocket.send(
              envelope && messageIds.has(envelope.msgId)
                ? JSON.stringify({ ...envelope, msgId: messageIds.get(envelope.msgId) })
                : payload
            );
            cursor += 1;
            continue;
          }
          return;
        }

        if (record.rest.direction === 'request') {
          const unmatched = pendingRestRequests.filter(entry => !entry.requestMatched);
          const pending = unmatched.find(entry => restRequestMatches(record.rest.request, entry.request));
          if (!pending) {
            // Allow arrival-order changes within a consecutive request batch, but not across barriers.
            const batch = [];
            for (let index = cursor; index < records.length; index += 1) {
              const candidate = records[index];
              if (candidate.kind !== 'rest' || candidate.rest.direction !== 'request') {
                break;
              }
              batch.push(candidate.rest.request);
            }
            const unexpected = unmatched.find(
              entry => !batch.some(expected => restRequestMatches(expected, entry.request))
            );
            if (unexpected) {
              assertRestRequestMatches(
                record.rest.request,
                summarizeRequest(unexpected.request),
                pendingKey(unexpected)
              );
            }
            return;
          }
          const requestKey = pendingKey(pending);
          assertRestRequestMatches(record.rest.request, summarizeRequest(pending.request), requestKey);
          pending.requestMatched = true;
          cursor += 1;
          continue;
        }

        const responseKey = `${record.rest.request.method} ${record.rest.request.url}`;
        const keyed = pendingRestRequests.filter(entry => pendingKey(entry) === responseKey);
        // Match concurrent requests by shape, not arrival order.
        const shaped = keyed.filter(entry => restRequestMatches(record.rest.request, entry.request));
        const pending = shaped.find(entry => entry.requestMatched) ?? shaped[0];
        if (!pending) {
          const drifted = keyed.find(entry => entry.requestMatched) ?? keyed[0];
          if (drifted) {
            // Matching method and URL with a different request shape is fixture drift.
            assertRestRequestMatches(record.rest.request, summarizeRequest(drifted.request), responseKey);
          }
          const waiting = pendingRestRequests.find(entry => !entry.requestMatched);
          if (waiting) {
            throw new Error(`REST fixture request out of order: expected ${responseKey}, got ${pendingKey(waiting)}`);
          }
          return;
        }
        pending.requestMatched = true;
        removePendingRestRequest(pending);
        inFlightRestDeliveries.add(pending);
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
          // Continue independent routes, but retain delivery failures for assertComplete().
          deliveryError ??= error;
          pending.reject(error);
        } finally {
          inFlightRestDeliveries.delete(pending);
        }
      }
    } catch (error) {
      failPendingRestRequests(error);
      throw error;
    } finally {
      draining = false;
      if (drainRequested) {
        drainRequested = false;
        void drain().catch(() => undefined);
      }
    }
  };

  return {
    install: async page => {
      await page.route('**/api/**', async (route, request) => {
        if (!isNotebookRestUrl(request.url())) {
          // fallback() preserves earlier route handlers; continue() bypasses them.
          await route.fallback?.();
          return;
        }
        let resolve;
        let reject;
        const completed = new Promise((resolvePromise, rejectPromise) => {
          resolve = resolvePromise;
          reject = rejectPromise;
        });
        completed.catch(() => undefined);
        if (fatalError) {
          throw fatalError;
        }
        const pending = { reject, request, requestMatched: false, resolve, route };
        pendingRestRequests.push(pending);
        try {
          await drain();
          if (fatalError && pendingRestRequests.includes(pending)) {
            throw fatalError;
          }
          const next = nextRecord();
          if (
            pendingRestRequests.includes(pending) &&
            !pending.requestMatched &&
            next?.kind === 'websocket' &&
            next.websocket.direction === 'send' &&
            // Allow an early request if a later record answers it.
            !hasRemainingResponseFor(pendingKey(pending))
          ) {
            removePendingRestRequest(pending);
            throw new Error(`Transport fixture out of order: expected WebSocket send, got REST ${pendingKey(pending)}`);
          }
          if (pendingRestRequests.includes(pending) && !hasRemainingResponseFor(pendingKey(pending))) {
            removePendingRestRequest(pending);
            throw new Error(`REST fixture has no remaining response for ${pendingKey(pending)}`);
          }
        } catch (error) {
          // Fixture errors invalidate every waiting route.
          removePendingRestRequest(pending);
          failPendingRestRequests(error);
          throw error;
        }

        try {
          await completed;
        } catch (error) {
          // A route delivery failure leaves independent routes available.
          removePendingRestRequest(pending);
          throw error;
        }
      });
      const failFixture = error => {
        failPendingRestRequests(error);
        throw error;
      };
      await page.routeWebSocket(
        url => isNotebookWebSocketUrl(url),
        ws => {
          if (webSocket) {
            throw new Error('Transport fixture supports one WebSocket connection per fixture');
          }
          webSocket = ws;
          void drain().catch(() => undefined);
          ws.onMessage(message => {
            const record = nextRecord();
            if (!record) {
              failFixture(
                new Error(
                  `WebSocket fixture messages exhausted before client send: ${stringifyWebSocketMessage(message)}`
                )
              );
            }
            if (record.kind !== 'websocket' || record.websocket.direction !== 'send') {
              failFixture(
                new Error(
                  `Transport fixture out of order: expected ${describeReplayRecord(record)}, got WebSocket send`
                )
              );
            }
            const expectedPayload = deserializeWebSocketPayload(record.websocket);
            const expected = parseEnvelope(expectedPayload);
            const actual = parseEnvelope(stringifyWebSocketMessage(message));
            let comparisonPayload = expectedPayload;
            if (expected && typeof expected.msgId === 'string') {
              const id = expected.msgId;
              const liveId = actual?.msgId;
              if (
                typeof liveId !== 'string' ||
                !liveId ||
                (recordedMessageIds.has(liveId) && liveId !== id) ||
                (messageIds.has(id) && messageIds.get(id) !== liveId) ||
                (runtimeMessageIds.has(liveId) && runtimeMessageIds.get(liveId) !== id)
              ) {
                failFixture(new Error('WebSocket fixture msgId correlation mismatch'));
              }
              comparisonPayload = JSON.stringify({ ...expected, msgId: liveId });
            }
            if (!webSocketPayloadMatches(comparisonPayload, message)) {
              failFixture(
                new Error(
                  `WebSocket fixture send mismatch: expected ${expectedPayload}, got ${stringifyWebSocketMessage(message)}`
                )
              );
            }
            if (expected && typeof expected.msgId === 'string') {
              messageIds.set(expected.msgId, actual.msgId);
              runtimeMessageIds.set(actual.msgId, expected.msgId);
            }
            cursor += 1;
            void drain().catch(() => undefined);
          });
        }
      );
    },
    assertComplete: () => {
      if (fatalError) {
        throw fatalError;
      }
      if (deliveryError) {
        throw deliveryError;
      }
      if (records.some(record => record.kind === 'websocket') && !webSocket) {
        throw new Error('WebSocket fixture was never connected');
      }
      const waiting = pendingRestRequests.length + inFlightRestDeliveries.size;
      if (waiting > 0 || cursor !== records.length) {
        const unconsumed = records.length - cursor;
        throw new Error(
          `Transport fixture has ${unconsumed} unconsumed record(s) and ${waiting} unfulfilled REST route(s)`
        );
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
  const outstandingRequests = new Set();
  const responseRecords = new Map();
  let sequence = 0;
  let captureFailure;
  let webSocketSeen = false;

  const settleCaptures = async () => {
    while (pending.size > 0) {
      await Promise.all([...pending]);
    }
    if (outstandingRequests.size > 0) {
      captureFailure ??= new Error(`${outstandingRequests.size} outstanding notebook request(s) without a response`);
    }
    if (captureFailure) {
      throw new Error(`Notebook transport capture failed: ${captureFailure?.message ?? captureFailure}`, {
        cause: captureFailure
      });
    }
  };

  const record = value => {
    const entry = {
      ...value,
      sequence: ++sequence
    };
    records.push(entry);
    return entry;
  };

  // Track subscriptions so stop() excludes traffic from later navigation or teardown.
  let installedPage;
  const subscriptions = [];
  const subscribe = (target, event, handler) => {
    target.on(event, handler);
    subscriptions.push([target, event, handler]);
  };

  return {
    install: page => {
      // Duplicate listeners produce a doubled capture that still passes structural validation.
      if (installedPage) {
        throw new Error('Transport recorder is already installed on a page');
      }
      installedPage = page;
      subscribe(page, 'request', request => {
        if (!isNotebookRestUrl(request.url())) {
          return;
        }
        outstandingRequests.add(request);
        record({
          kind: 'rest',
          rest: {
            direction: 'request',
            request: summarizeRequest(request)
          }
        });
      });
      subscribe(page, 'response', async response => {
        const request = response.request();
        if (!isNotebookRestUrl(request.url())) {
          return;
        }
        outstandingRequests.add(request);
        const rest = {
          direction: 'response',
          headers: filterHeaders(response.headers()),
          request: summarizeRequest(request),
          status: response.status(),
          bodyRaw: ''
        };
        responseRecords.set(request, rest);
        const bodyRead = response
          .text()
          .then(body => {
            const parsed = parseRestBody(body, response.headers());
            if ('bodyJson' in parsed) delete rest.bodyRaw;
            Object.assign(rest, parsed);
          })
          .catch(error => {
            captureFailure ??= error;
          })
          .finally(() => pending.delete(bodyRead));
        pending.add(bodyRead);
      });
      subscribe(page, 'requestfinished', request => {
        const rest = responseRecords.get(request);
        if (!rest) return;
        // response fires at headers; text() returns asynchronously.
        // Reserve ordering at download completion, before later WebSocket frames.
        record({ kind: 'rest', rest });
        responseRecords.delete(request);
        outstandingRequests.delete(request);
      });
      // Retain the transport error for stop() and write().
      subscribe(page, 'requestfailed', request => {
        if (!isNotebookRestUrl(request.url())) {
          return;
        }
        outstandingRequests.delete(request);
        responseRecords.delete(request);
        const reason = request.failure?.()?.errorText ?? 'unknown error';
        captureFailure ??= new Error(
          `Notebook request failed during capture: ${request.method()} ${request.url()} (${reason})`
        );
      });
      subscribe(page, 'websocket', socket => {
        if (!isNotebookWebSocketUrl(socket.url())) {
          return;
        }
        if (webSocketSeen) {
          captureFailure ??= new Error('Transport capture supports one WebSocket connection per fixture');
          return;
        }
        webSocketSeen = true;
        const captureFrame = direction => frame => {
          try {
            recordCapturedWebSocketFrame(record, direction, framePayload(frame));
          } catch (error) {
            // Retain listener errors so stop() and write() reject incomplete captures.
            captureFailure ??= error;
            throw error;
          }
        };
        subscribe(socket, 'framesent', captureFrame('send'));
        subscribe(socket, 'framereceived', captureFrame('receive'));
      });
    },
    stop: async () => {
      const detach = ([target, event, handler]) => (target.off ?? target.removeListener)?.call(target, event, handler);
      const finishing = [];
      for (const subscription of subscriptions.splice(0)) {
        // Finish responses already being read while refusing new capture traffic.
        if (subscription[1] === 'requestfinished') finishing.push(subscription);
        else detach(subscription);
      }
      try {
        await settleCaptures();
      } finally {
        finishing.forEach(detach);
      }
    },
    snapshot: () => sanitizeFixture({ metadata, records: [...records], version: fixtureVersion }),
    write: async fixturePath => {
      await settleCaptures();
      const sanitized = sanitizeFixture({ metadata, records: [...records], version: fixtureVersion });
      mkdirSync(path.dirname(fixturePath), { recursive: true });
      writeFileSync(fixturePath, `${JSON.stringify(sanitized, null, 2)}\n`);
      return sanitized;
    }
  };
}

function serializeRestBody(rest) {
  if ('bodyJson' in rest) {
    return JSON.stringify(rest.bodyJson);
  }
  return rest.bodyRaw ?? '';
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

function stringifyWebSocketMessage(message) {
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
        stableJson(normalizeEnvelope(JSON.parse(actualPayload))) ===
        stableJson(normalizeEnvelope(JSON.parse(expectedPayload)))
      );
    } catch {
      // Invalid JSON was captured as raw text and must use the same comparison rules.
    }
  }
  // Redact both sides identically; existing placeholders remain unchanged.
  return redactRawSensitiveValues(expectedPayload) === redactRawSensitiveValues(actualPayload);
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
      .filter(([key, value]) => safeHeaderNames.has(key) && !isDefaultAccept(key, value))
  );
}

// Playwright may omit Accept during capture but expose its default */* during routing.
// Treat both forms as the same request.
function isDefaultAccept(key, value) {
  return key === 'accept' && value.trim() === '*/*';
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

function parseEnvelope(payload) {
  if (typeof payload !== 'string') {
    return undefined;
  }
  try {
    const value = JSON.parse(payload);
    return value && typeof value === 'object' && !Array.isArray(value) ? value : undefined;
  } catch {
    return undefined;
  }
}

// Preserve top-level msgId for request/reply correlation.
// Nested IDs keep their normal redaction rules.
function normalizeEnvelope(value) {
  const normalized = normalizeFixtureRecord(value);
  if (value && !Array.isArray(value) && typeof value === 'object' && Object.hasOwn(value, 'msgId')) {
    normalized.msgId = value.msgId;
  }
  return normalized;
}

function sanitizeWebSocket(websocket) {
  if (!websocket?.payloadText) {
    return websocket;
  }
  if (!looksLikeJson(websocket.payloadText)) {
    return { ...websocket, payloadText: redactRawSensitiveValues(websocket.payloadText) };
  }
  try {
    return {
      ...websocket,
      payloadText: JSON.stringify(normalizeEnvelope(JSON.parse(websocket.payloadText)))
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

function restRequestMatches(expectedRequest, actualRequest) {
  const expected = normalizeFixtureRecord(sanitizeRestRequest(expectedRequest));
  const actual = normalizeFixtureRecord(sanitizeRestRequest(summarizeRequest(actualRequest)));
  return stableJson(expected) === stableJson(actual);
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

// Match sensitive suffixes such as accessToken and PGPASSWORD, but not tokenizer.
// Avoid a prefix quantifier to keep matching linear.
const sensitiveWordPattern =
  '(?:api[-_]?key|authorization|client[-_]?secret|cookie|credential(?:s)?|jsessionid|passphrase|passwd|password|principal|private[-_]?key|secret|ticket|token)';
const sensitiveNamePattern = `${sensitiveWordPattern}(?![A-Za-z0-9_])`;
const sensitiveWholeWordPattern = new RegExp(`^${sensitiveWordPattern}$`, 'i');
const sensitiveSuffixPattern = new RegExp(`${sensitiveWordPattern}$`, 'i');
// Bound the candidate-name scan on both sides to keep it linear.
const nameScanPattern = `[A-Za-z0-9_.-]{0,40}${sensitiveWordPattern}[A-Za-z0-9_.-]{0,40}`;

// Split separators and camel case to find credential names without matching ordinary words.
// Join adjacent parts for names such as apiKey and client_secret.
const nameWords = name =>
  String(name)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Za-z])([0-9])/g, '$1 $2')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean);

const sensitiveAnywherePattern = new RegExp(sensitiveWordPattern, 'i');

function shouldRedactField(key) {
  // Uppercase names such as AWSSECRETKEY have no camel-case boundaries.
  if (/^[A-Z0-9_]+$/.test(key) && sensitiveAnywherePattern.test(key)) {
    return true;
  }
  // Also recognize unsplit suffixes such as PGPASSWORD.
  if (sensitiveSuffixPattern.test(key)) {
    return true;
  }
  const words = nameWords(key);
  return words.some(
    (word, index) =>
      sensitiveWholeWordPattern.test(word) ||
      (index + 1 < words.length && sensitiveWholeWordPattern.test(word + words[index + 1]))
  );
}
// Group header alternatives so the separator applies to every name.
const sensitiveHeaderNamePattern = '(?:(?:proxy-)?authorization|(?:set-)?cookie)(?![A-Za-z0-9_])';
// Preserve exact placeholders, including URL-encoded forms, to keep redaction idempotent.
// Named placeholders must match the field; prefixes and mismatched names are not safe.
// For example, password=<redacted>REAL_SECRET and password=<private> must be masked.
const placeholderValuePattern = /^(?:<([A-Za-z0-9_.-]*)>|%3[Cc]([A-Za-z0-9_.-]*)%3[Ee])$/;
const isPlaceholderValue = (name, body) => {
  const match = placeholderValuePattern.exec(body);
  if (!match) {
    return false;
  }
  const inner = match[1] ?? match[2];
  return inner === 'redacted' || inner.toLowerCase() === String(name).toLowerCase();
};
// Unquoted values end at whitespace or structural delimiters.
// Credentials containing spaces must be quoted to be masked whole.
const unquotedValuePattern = '(?:[{\\[][^\\r\\n]*|[^\\s,;&#}\\]"\'\\r\\n]+)';

// Preserve numeric `principal = 1000` in raw text for accounting content.
// Structured principal fields and all other credential names are always masked.
const numericValueNames = /principal$/i;
const keepsNumericValue = (name, body) => numericValueNames.test(name) && /^\d+(?:\.\d+)?$/.test(body);

function redactEmbeddedSecrets(value) {
  const text = String(value);
  // Skip repeated name scans when no assignment delimiter exists.
  if (!text.includes(':') && !text.includes('=')) {
    return text;
  }
  return (
    text
      // URL credentials are identified by position, without a field name.
      .replace(/(:\/\/[^\s/:@]+:)(?!<redacted>@)([^\s@/]+)(?=@)/g, (_match, prefix) => `${prefix}<redacted>`)
      // ?access_token=abc&view=stable
      .replace(new RegExp(`([?&](${sensitiveNamePattern})=)([^&#\\s]+)`, 'gi'), (match, prefix, name, body) =>
        isPlaceholderValue(name, body) ? match : `${prefix}<redacted>`
      )
      // Preserve quotes for valid syntax and repeatable redaction.
      .replace(
        new RegExp(`(["']?(${nameScanPattern})["']?\\s*(?::|={1,3})\\s*)(["'])((?:\\\\.|(?!\\3)[^\\\\])*)\\3`, 'gi'),
        (match, prefix, name, quote, body) =>
          shouldRedactField(name) && !isPlaceholderValue(name, body) ? `${prefix}${quote}<redacted>${quote}` : match
      )
      // Header credentials can contain spaces and extend to the end of the line.
      // Reject leading whitespace so a second pass leaves placeholders intact.
      .replace(
        new RegExp(
          `(${sensitiveHeaderNamePattern}\\s*[:=](?![=])\\s*)(?!<redacted>)([^\\s,;}\\]"']["']?[^,;}\\]"'\\r\\n]*|[^\\s,;}\\]"'\\r\\n])`,
          'gi'
        ),
        (_match, prefix) => `${prefix}<redacted>`
      )
      // Match keys at unindented line, string, object or array boundaries.
      // Leave prose such as `buy a ticket: today`, indented YAML and list items outside this rule.
      .replace(
        new RegExp(
          `(^|[{,"']\\s*)((["']?)(${nameScanPattern})\\3\\s*:(?![=])\\s*["']?)(${unquotedValuePattern})`,
          'gim'
        ),
        (match, lead, prefix, _quote, name, body) =>
          shouldRedactField(name) && !keepsNumericValue(name, body) && !isPlaceholderValue(name, body)
            ? `${lead}${prefix}<redacted>`
            : match
      )
      // export PGPASSWORD=x, ;password=x, token=x&user=bob, accessToken = "x"
      .replace(
        new RegExp(`((${nameScanPattern})\\s*=(?![=])\\s*["']?)(${unquotedValuePattern})`, 'gi'),
        (match, prefix, name, body) =>
          shouldRedactField(name) && !keepsNumericValue(name, body) && !isPlaceholderValue(name, body)
            ? `${prefix}<redacted>`
            : match
      )
  );
}

// Use identical redaction for captured raw payloads and live frames.
const redactRawSensitiveValues = redactEmbeddedSecrets;

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
  const hasText = 'payloadText' in record.websocket;
  const hasBase64 = 'payloadBase64' in record.websocket;
  if (!hasText && !hasBase64) {
    errors.push(`${prefix}.websocket payloadText or payloadBase64 is required to preserve message shape`);
  } else if (hasText && hasBase64) {
    errors.push(`${prefix}.websocket must contain exactly one of payloadText or payloadBase64`);
  }
  // Reject malformed payloads before string coercion or permissive Base64 decoding loses data.
  if (hasText && typeof record.websocket.payloadText !== 'string') {
    errors.push(`${prefix}.websocket.payloadText must be a string`);
  }
  if (hasBase64 && !isBase64(record.websocket.payloadBase64)) {
    errors.push(`${prefix}.websocket.payloadBase64 must be base64`);
  }
};

// Buffer.from silently drops invalid Base64 characters.
// Require a round trip, allowing equivalent padding.
const isBase64 = value => {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) {
    return false;
  }
  const unpadded = text => text.replace(/=+$/, '');
  return unpadded(Buffer.from(value, 'base64').toString('base64')) === unpadded(value);
};

const isHttpMethod = value => typeof value === 'string' && /^[A-Z]+$/.test(value);

const isHeaderRecord = value =>
  Boolean(value) &&
  typeof value === 'object' &&
  !Array.isArray(value) &&
  Object.values(value).every(entry => typeof entry === 'string');

// bodyRaw must be a string; bodyJson accepts any JSON value, including null.
const hasRestBody = value =>
  Boolean(value) && ('bodyJson' in value || ('bodyRaw' in value && typeof value.bodyRaw === 'string'));

const looksLikeJson = value => /^[{[]/.test(String(value).trim());
