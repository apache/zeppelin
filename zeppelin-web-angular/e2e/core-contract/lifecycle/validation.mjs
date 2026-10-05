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

import {
  stableJson,
  validateFixtureMetadata,
  validateRestRecord,
  validateWebSocketRecord
} from '../transport/fixture.mjs';

export const lifecycleFixtureVersion = 2;

const nonEmptyString = value => typeof value === 'string' && value.length > 0;

function validateConnection(errors, prefix, record, state) {
  const { sessionId, connectionId, event } = record;

  if (typeof connectionId !== 'string' || !connectionId) {
    errors.push(`${prefix}: connectionId required`);
  }

  if (event === 'open') {
    if (state.connections.has(connectionId) || state.activeConnections.has(sessionId)) {
      errors.push(`${prefix}: duplicate or overlapping connection`);
    }

    state.connections.add(connectionId);
    state.activeConnections.set(sessionId, connectionId);
    return;
  }

  if (event === 'close') {
    if (state.activeConnections.get(sessionId) !== connectionId) {
      errors.push(`${prefix}: closing inactive connection`);
    }

    state.activeConnections.delete(sessionId);
    return;
  }

  errors.push(`${prefix}: unknown connection event`);
}

function validateRouteContext(errors, prefix, record) {
  const context = record.context;

  if (!context) {
    return errors.push(`${prefix}: invalid route context: missing context`);
  }
  if (!['active', 'inactive'].includes(context.state)) {
    errors.push(`${prefix}: invalid route context: unknown state`);
  }
  if (!nonEmptyString(context.noteId)) {
    errors.push(`${prefix}: invalid route context: noteId required`);
  }
  if (context.revisionId !== null && !nonEmptyString(context.revisionId)) {
    errors.push(`${prefix}: invalid route context: revisionId must be null or a non-empty string`);
  }
}

function validateLifecycleRest(errors, prefix, record, state) {
  validateRestRecord(errors, prefix, record);

  if (typeof record.requestId !== 'string' || !record.requestId) {
    errors.push(`${prefix}: requestId required`);
  }

  const requestId = `${record.sessionId}:${record.requestId}`;
  const rest = record.rest;

  if (rest?.direction === 'request') {
    if (state.seenRequests.has(requestId)) {
      errors.push(`${prefix}: duplicate REST request`);
    }

    state.seenRequests.add(requestId);
    state.requests.set(requestId, rest.request);
  } else if (rest?.direction === 'response') {
    if (!Number.isInteger(rest.status) || rest.status < 100 || rest.status > 599) {
      errors.push(`${prefix}: response status required`);
    }

    if (!state.requests.has(requestId) || stableJson(state.requests.get(requestId)) !== stableJson(rest.request)) {
      errors.push(`${prefix}: unmatched REST response`);
    }

    state.requests.delete(requestId);
  }
}

function validateLifecycleFrame(errors, prefix, record, state) {
  validateWebSocketRecord(errors, prefix, record);

  if (
    record.delivery !== undefined &&
    (record.delivery !== 'dropped-before-server' || record.websocket?.direction !== 'send')
  ) {
    errors.push(`${prefix}: invalid dropped-send observation`);
  }

  if (!record.connectionId || state.activeConnections.get(record.sessionId) !== record.connectionId) {
    errors.push(`${prefix}: frame on inactive connection`);
  }

  let envelope;
  try {
    envelope = JSON.parse(record.websocket?.payloadText);
  } catch {
    errors.push(`${prefix}: lifecycle replay requires a JSON envelope`);
    return;
  }

  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) {
    errors.push(`${prefix}: lifecycle replay requires a JSON envelope`);
    return;
  }

  if (!nonEmptyString(envelope.op)) {
    errors.push(`${prefix}: lifecycle envelope op must be a non-empty string`);
  }

  if (
    envelope.msgId !== undefined &&
    envelope.msgId !== null &&
    (typeof envelope.msgId !== 'string' || !envelope.msgId || envelope.msgId === '<msgId>')
  ) {
    errors.push(`${prefix}: ambiguous or invalid msgId; recapture the fixture`);
  }
}

const recordValidators = {
  connection: validateConnection,
  context: validateRouteContext,
  rest: validateLifecycleRest,
  websocket: validateLifecycleFrame
};

function validateClientDelivery(errors, fixture) {
  const observation = fixture.metadata?.routeTransition;
  if (!observation) return;

  const receives = fixture.records.filter(
    record =>
      record?.kind === 'websocket' &&
      record.sessionId === observation.sessionId &&
      record.websocket?.direction === 'receive'
  );
  const sequences = observation.deliveredSequences;

  if (observation.boundary !== 'browser-message') {
    errors.push('client delivery requires the browser-message observation boundary');
  }

  if (!nonEmptyString(observation.intervention)) {
    errors.push('client delivery intervention required');
  }

  if (!Array.isArray(sequences)) {
    errors.push('client delivery sequences must be an array');
    return;
  }

  const receivedSequences = new Set(receives.map(record => record.sequence));
  const referencesEveryOccurrence =
    sequences.length === receivedSequences.size &&
    new Set(sequences).size === sequences.length &&
    sequences.every(sequence => receivedSequences.has(sequence));

  if (!referencesEveryOccurrence) {
    errors.push('client delivery must reference every upstream receive occurrence exactly once');
    return;
  }
  const held = receives.find(record => record.sequence === observation.heldNoteSequence);

  let envelope;
  try {
    envelope = held && JSON.parse(held.websocket.payloadText);
  } catch {
    errors.push('client delivery references an invalid envelope');
    return;
  }

  const index = sequences.indexOf(observation.heldNoteSequence);
  const identifiesHeldNote = envelope?.op === 'NOTE' && envelope.data?.note?.id === observation.noteId;
  const followsReleaseBoundary =
    index > 0 &&
    sequences[index - 1] === observation.releaseAfterSequence &&
    observation.releaseAfterSequence > observation.heldNoteSequence;

  if (!identifiesHeldNote) {
    errors.push('client delivery must identify the held NOTE and observed release boundary: wrong NOTE');
    return;
  }
  if (!followsReleaseBoundary) {
    errors.push('client delivery must identify the held NOTE and observed release boundary');
    return;
  }

  const expected = receives
    .map(record => record.sequence)
    .filter(sequence => sequence !== observation.heldNoteSequence);
  expected.splice(expected.indexOf(observation.releaseAfterSequence) + 1, 0, observation.heldNoteSequence);

  if (stableJson(expected) !== stableJson(sequences)) {
    errors.push('client delivery changed order beyond the declared NOTE hold');
  }
}

// Context belongs to the capture, never to an untagged server frame.
export function validateLifecycleFixture(fixture) {
  const errors = [];

  if (fixture?.version !== lifecycleFixtureVersion) {
    errors.push('Unsupported lifecycle fixture version; recapture v1');
  }

  validateFixtureMetadata(errors, fixture?.metadata);

  if (!Array.isArray(fixture?.records) || !fixture.records.length) {
    return [...errors, 'records must be non-empty'];
  }

  if (!Array.isArray(fixture.sessions) || !fixture.sessions.length) {
    return [...errors, 'sessions must be non-empty'];
  }

  const sessions = new Set();
  for (const session of fixture.sessions) {
    if (typeof session?.id !== 'string' || !session.id || sessions.has(session.id)) {
      errors.push('session IDs must be unique non-empty strings');
    }

    sessions.add(session?.id);
  }

  const state = {
    connections: new Set(),
    activeConnections: new Map(),
    requests: new Map(),
    seenRequests: new Set()
  };

  for (const [index, record] of fixture.records.entries()) {
    const prefix = `records[${index}]`;

    if (record?.sequence !== index + 1) {
      errors.push(`${prefix}: contiguous sequence required; ordering or records lost`);
    }

    if (!sessions.has(record?.sessionId)) {
      errors.push(`${prefix}: unknown session`);
    }

    const validateRecord = recordValidators[record?.kind];
    if (Object.hasOwn(recordValidators, record?.kind)) {
      validateRecord(errors, prefix, record, state);
    } else {
      errors.push(`${prefix}: unknown record kind`);
    }
  }

  validateClientDelivery(errors, fixture);

  if (state.requests.size) {
    errors.push('unfinished REST requests');
  }

  return [...new Set(errors)];
}
