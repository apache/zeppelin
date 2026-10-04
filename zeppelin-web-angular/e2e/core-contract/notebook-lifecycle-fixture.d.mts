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
import type { Page } from '@playwright/test';
import type { FixtureMetadata, FixtureRest, FixtureWebSocket } from './notebook-transport-fixture.mjs';

export interface RouteContext {
  state: 'active' | 'inactive';
  noteId: string;
  revisionId: string | null;
}
interface SessionRecord {
  sequence: number;
  sessionId: string;
}

export type LifecycleRecord = SessionRecord &
  (
    | { kind: 'connection'; event: 'open' | 'close'; connectionId: string }
    | { kind: 'context'; context: RouteContext }
    | { kind: 'rest'; requestId: string; rest: FixtureRest }
    | {
        kind: 'websocket';
        connectionId: string;
        delivery?: 'dropped-before-server';
        websocket: FixtureWebSocket & { payloadText: string };
      }
  );
export interface LifecycleFixture {
  version: number;
  metadata: LifecycleMetadata;
  sessions: { id: string }[];
  records: LifecycleRecord[];
}

export interface LifecycleMetadata extends FixtureMetadata {
  commitLoss?: {
    loss: 'request' | 'reply';
    faults: LifecycleFault[];
    observationDeadlineMs: number;
    localDraft: string;
    canonicalText: string;
    reconciliation: string;
    protocolGap: string;
    lifecycleGate: string;
  };
  authoritativeInputs?: {
    ingress: 'rest' | 'websocket';
    commands: string[];
    frames: string[];
  }[];
}
export interface LifecycleFault {
  sequence: number;
  copies?: number;
  delayMs?: number;
  afterSequence?: number;
}
export interface LifecycleRecorder {
  install(page: Page, sessionId: string): void;
  context(sessionId: string, context: RouteContext): void;
  droppedSend(sessionId: string, payloadText: string): void;
  snapshot(): LifecycleFixture;
  stop(): Promise<void>;
  write(file: string): Promise<LifecycleFixture>;
}
export interface LifecycleReplay {
  install(page: Page, sessionId: string): Promise<void>;
  context(sessionId: string, context: RouteContext): void;
  assertComplete(): void;
  position(): number;
  dispose(): void;
}
export declare const lifecycleFixtureVersion: number;
export declare function validateLifecycleFixture(fixture: unknown): string[];
export declare function createLifecycleRecorder(metadata: LifecycleMetadata): LifecycleRecorder;
export declare function createLifecycleReplay(fixture: LifecycleFixture, faults?: LifecycleFault[]): LifecycleReplay;
