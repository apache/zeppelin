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

import { BehaviorSubject, concatMap, filter, from, map, mergeMap, of, take, tap, timer } from 'rxjs';
import { compileDeliveryPlan } from './replay-plan.mjs';

export function createLifecycleDeliveryScheduler(fixture, faults, consumed$, deliver, onError) {
  const releases = compileDeliveryPlan(fixture, faults);

  const completed$ = new BehaviorSubject(false);
  const subscription = from(releases)
    .pipe(
      // Only the next release boundary is observed; timer deliveries continue independently.
      concatMap(([boundary, batch]) =>
        consumed$.pipe(
          filter(sequence => sequence >= boundary),
          take(1),
          map(() => batch)
        )
      ),
      mergeMap(batch =>
        from(batch).pipe(
          mergeMap(entry =>
            (entry.delayMs ? timer(entry.delayMs) : of(0)).pipe(tap(() => deliver(entry.record, entry.copies)))
          )
        )
      )
    )
    .subscribe({ complete: () => completed$.next(true), error: onError });

  return {
    completed$,
    hasPending() {
      return !completed$.value;
    },
    dispose() {
      subscription.unsubscribe();
    }
  };
}
