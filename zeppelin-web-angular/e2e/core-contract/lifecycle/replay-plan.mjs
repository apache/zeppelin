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

import { stableJson, webSocketPayloadMatches } from '../transport/fixture.mjs';

export const matchesRequest = (expected, actual) => {
  const normalize = value => ({ ...value, headers: { accept: '*/*', ...value.headers } });
  return stableJson(normalize(expected)) === stableJson(normalize(actual));
};

export function consecutiveRecords(records, cursor, predicate) {
  const batch = [];

  for (let index = cursor; index < records.length; index++) {
    const record = records[index];
    if (!predicate(record)) break;
    batch.push(record);
  }

  return batch;
}

export function createMessageCorrelation(fixture) {
  const sentIds = new Set();
  const receivedIds = new Set();

  for (const record of fixture.records) {
    if (record.kind !== 'websocket') continue;
    const { msgId } = JSON.parse(record.websocket.payloadText);
    if (typeof msgId === 'string') {
      (record.websocket.direction === 'send' ? sentIds : receivedIds).add(msgId);
    }
  }
  return { recorded: new Map(), runtime: new Map(), remote: new Set([...receivedIds].filter(id => !sentIds.has(id))) };
}

export function matchOutgoingFrame(record, payload, state) {
  const expected = JSON.parse(record.websocket.payloadText);
  const actual = JSON.parse(String(payload));
  const recordedId = expected.msgId;
  const runtimeId = actual.msgId;

  if (recordedId === null && runtimeId !== null) {
    throw new Error('Lifecycle msgId correlation mismatch');
  }

  if (typeof recordedId === 'string') {
    if (typeof runtimeId !== 'string' || !runtimeId) {
      throw new Error('Lifecycle msgId correlation mismatch: runtime ID must be a non-empty string');
    }
    if (state.remote.has(runtimeId)) {
      throw new Error('Lifecycle msgId correlation mismatch: runtime ID collides with a remote ID');
    }
    if (state.recorded.has(recordedId) && state.recorded.get(recordedId) !== runtimeId) {
      throw new Error('Lifecycle msgId correlation mismatch: recorded ID already has a different binding');
    }
    if (state.runtime.has(runtimeId) && state.runtime.get(runtimeId) !== recordedId) {
      throw new Error('Lifecycle msgId correlation mismatch: runtime ID already belongs to another recorded ID');
    }

    expected.msgId = runtimeId;
  }

  if (!webSocketPayloadMatches(JSON.stringify(expected), payload)) {
    throw new Error('Lifecycle frame mismatch');
  }

  return typeof recordedId === 'string' ? [recordedId, runtimeId] : null;
}

export function incomingFrame(record, state) {
  const envelope = JSON.parse(record.websocket.payloadText);
  const binding = state.recorded.get(envelope.msgId);

  if (typeof envelope.msgId === 'string' && !binding) {
    if (state.runtime.has(envelope.msgId) && state.runtime.get(envelope.msgId) !== envelope.msgId) {
      throw new Error('Lifecycle msgId correlation mismatch');
    }
    return { binding: [envelope.msgId, envelope.msgId], payload: record.websocket.payloadText };
  }

  return {
    binding: null,
    payload: binding ? JSON.stringify({ ...envelope, msgId: binding }) : record.websocket.payloadText
  };
}

const maximumTimerDelay = 2_147_483_647;

function requireIntegerRange(value, minimum, maximum, field) {
  const validIntegerRange = Number.isInteger(value) && value >= minimum && value <= maximum;
  if (!validIntegerRange) {
    throw new Error(`Fault ${field} must be an integer between ${minimum} and ${maximum}`);
  }
}

function validateFault(fault, records) {
  requireIntegerRange(fault.sequence, 1, records.length, 'sequence');

  const record = records[fault.sequence - 1];
  if (record.kind !== 'websocket') {
    throw new Error('Fault must select a receive frame');
  }
  if (record.websocket.direction !== 'receive') {
    throw new Error('Fault must select a receive frame');
  }

  requireIntegerRange(fault.copies ?? 1, 0, 10, 'copies');
  requireIntegerRange(fault.afterSequence ?? fault.sequence, fault.sequence, records.length, 'afterSequence');

  const delay = fault.delayMs ?? 0;
  if (!Number.isFinite(delay)) {
    throw new Error('Fault delayMs must be finite');
  }
  if (delay < 0 || delay > maximumTimerDelay) {
    throw new Error(`Fault delayMs must be between 0 and ${maximumTimerDelay}`);
  }
}

export function compileDeliveryPlan(fixture, faults) {
  const faultMap = new Map();
  for (const fault of faults) {
    validateFault(fault, fixture.records);

    if (faultMap.has(fault.sequence)) {
      throw new Error('Fault sequence must be unique');
    }
    faultMap.set(fault.sequence, fault);
  }

  // Compile stable release batches; later captured entries at the same boundary are delivered first.
  const releases = new Map();
  for (const record of fixture.records) {
    if (record.kind !== 'websocket' || record.websocket.direction !== 'receive') continue;

    const fault = faultMap.get(record.sequence) ?? {};
    const boundary = fault.afterSequence ?? record.sequence;
    const batch = releases.get(boundary) ?? [];

    batch.unshift({ record, copies: fault.copies ?? 1, delayMs: fault.delayMs ?? 0 });
    releases.set(boundary, batch);
  }

  return [...releases].sort(([first], [second]) => first - second);
}

export function requiresRuntimeInput(record) {
  switch (record.kind) {
    case 'context':
      return true;
    case 'connection':
      return record.event === 'open';
    case 'websocket':
      return record.websocket.direction === 'send';
    case 'rest':
      return record.rest.direction === 'request';
  }
}
