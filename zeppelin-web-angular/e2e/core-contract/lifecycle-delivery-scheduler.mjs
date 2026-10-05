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

function requireIntegerRange(value, minimum, maximum, field) {
  const valid = Number.isInteger(value) && value >= minimum && value <= maximum;
  if (!valid) throw new Error(`Fault ${field} must be an integer between ${minimum} and ${maximum}`);
}

function validateFault(fault, records) {
  requireIntegerRange(fault.sequence, 1, records.length, 'sequence');
  const record = records[fault.sequence - 1];
  if (record.kind !== 'websocket') throw new Error('Fault must select a receive frame');
  if (record.websocket.direction !== 'receive') throw new Error('Fault must select a receive frame');

  requireIntegerRange(fault.copies ?? 1, 0, 10, 'copies');
  requireIntegerRange(fault.afterSequence ?? fault.sequence, fault.sequence, records.length, 'afterSequence');
  const delay = fault.delayMs ?? 0;
  if (!Number.isFinite(delay) || delay < 0) throw new Error('Fault delayMs must be finite and non-negative');
}

export function createLifecycleDeliveryScheduler(fixture, faults, deliver, onError) {
  const faultMap = new Map();
  for (const fault of faults) {
    validateFault(fault, fixture.records);
    if (faultMap.has(fault.sequence)) throw new Error('Fault sequence must be unique');
    faultMap.set(fault.sequence, fault);
  }

  const deferred = [];
  const timers = new Set();
  const flush = cursor => {
    for (let index = deferred.length - 1; index >= 0; index--) {
      const entry = deferred[index];
      if (entry.afterSequence > cursor) continue;
      deferred.splice(index, 1);
      if (entry.delayMs) {
        const timer = setTimeout(() => {
          timers.delete(timer);
          try {
            deliver(entry.record, entry.copies);
          } catch (error) {
            onError(error);
          }
        }, entry.delayMs);
        timers.add(timer);
      } else deliver(entry.record, entry.copies);
    }
  };
  return {
    enqueue(record) {
      const fault = faultMap.get(record.sequence) ?? {};
      deferred.push({
        record,
        copies: fault.copies ?? 1,
        delayMs: fault.delayMs ?? 0,
        afterSequence: fault.afterSequence ?? record.sequence
      });
    },
    flush,
    hasPending() {
      return deferred.length > 0 || timers.size > 0;
    },
    dispose() {
      timers.forEach(timer => clearTimeout(timer));
      timers.clear();
      deferred.length = 0;
    }
  };
}
