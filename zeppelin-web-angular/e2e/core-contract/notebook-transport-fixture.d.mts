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

// validateFixture checks record shapes at runtime; callers use optional field access.

export interface FixtureRestRequest {
  bodyJson?: unknown;
  bodyRaw?: string;
  headers: Record<string, string>;
  method: string;
  url: string;
}

export interface FixtureRest {
  bodyJson?: unknown;
  bodyRaw?: string;
  direction: string;
  headers?: Record<string, string>;
  request: FixtureRestRequest;
  status?: number;
}

export interface FixtureWebSocket {
  direction: string;
  payloadBase64?: string;
  payloadText?: string;
}

export interface FixtureRecord {
  kind: string;
  rest?: FixtureRest;
  sequence: number;
  websocket?: FixtureWebSocket;
}

export interface FixtureMetadata {
  coveredOperations: string[];
  knownExclusions: string[];
  owner: string;
  scenario: string;
  // Recorded for provenance; neither is read or validated by this module.
  capturedAt?: string;
  zeppelinVersion?: string;
}

export interface TransportFixture {
  metadata?: FixtureMetadata;
  records: FixtureRecord[];
  version: number;
}

// Minimal interfaces shared by Playwright objects and test doubles.
// Untyped callbacks allow both implementations.
/* eslint-disable @typescript-eslint/no-explicit-any */
export interface FixtureRouteLike {
  fallback?: (...args: any[]) => any;
  fulfill: (...args: any[]) => any;
}

export interface FixtureRequestLike {
  headers: () => Record<string, string>;
  method: () => string;
  postData: () => string | null;
  url: () => string;
}

// Require only the page methods each adapter uses.
export interface RecorderPageLike {
  on: (...args: any[]) => any;
}

export interface ReplayPageLike {
  on?: (...args: any[]) => any;
  route: (...args: any[]) => any;
  routeWebSocket: (...args: any[]) => any;
}
/* eslint-enable @typescript-eslint/no-explicit-any */

export interface PlaywrightFixtureAdapter {
  assertComplete(): void;
  install(page: ReplayPageLike): Promise<void>;
}

export interface NotebookTransportRecorder {
  install(page: RecorderPageLike): void;
  /**
   * Await stop() before asserting or writing a snapshot; response bodies may still be pending.
   */
  snapshot(): TransportFixture;
  stop(): Promise<void>;
  write(fixturePath: string): Promise<TransportFixture>;
}

export declare const fixtureVersion: number;

// Placeholder substitution can change value types, including numbers to strings.
export declare function normalizeFixtureRecord(value: unknown): unknown;
export declare function sanitizeFixture(fixture: TransportFixture): TransportFixture;
export declare function validateFixture(fixture: unknown): string[];
export declare function validateReplayFixture(fixture: unknown): string[];

export declare function createPlaywrightFixtureAdapter(fixture: TransportFixture): PlaywrightFixtureAdapter;
export declare function createNotebookTransportRecorder(metadata: FixtureMetadata): NotebookTransportRecorder;

export declare function parseRestBody(
  body: string,
  headers?: Record<string, string>
): { bodyJson?: unknown; bodyRaw?: string };
export declare function webSocketPayloadMatches(expectedPayload: unknown, actualMessage: unknown): boolean;
export declare function isNotebookRestUrl(value: string): boolean;
