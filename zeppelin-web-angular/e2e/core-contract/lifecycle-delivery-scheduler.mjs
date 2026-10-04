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

export function createLifecycleDeliveryScheduler(fixture, faults, deliver, onError) {
  const faultMap = new Map();
  for (const fault of faults) {
    const record = fixture.records[fault.sequence - 1];
    if (
      record?.kind !== 'websocket' ||
      record.websocket.direction !== 'receive' ||
      faultMap.has(fault.sequence) ||
      !Number.isInteger(fault.copies ?? 1) ||
      (fault.copies ?? 1) < 0 ||
      (fault.copies ?? 1) > 10 ||
      !Number.isFinite(fault.delayMs ?? 0) ||
      (fault.delayMs ?? 0) < 0 ||
      !Number.isInteger(fault.afterSequence ?? fault.sequence) ||
      (fault.afterSequence ?? fault.sequence) < fault.sequence ||
      (fault.afterSequence ?? fault.sequence) > fixture.records.length
    ) {
      throw new Error('Fault must select a receive frame with valid copies, delayMs and afterSequence');
    }
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
