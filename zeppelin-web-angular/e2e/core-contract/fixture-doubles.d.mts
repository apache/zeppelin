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

import type { FixtureMetadata, FixtureRecord, FixtureRequestLike, FixtureRest } from './notebook-transport-fixture.mjs';

export declare const fixtureMetadata: () => FixtureMetadata;

export declare const request: (
  method: string,
  url: string,
  body?: string,
  headers?: Record<string, string>
) => FixtureRequestLike;

export declare const response: (
  sourceRequest: FixtureRequestLike,
  status: number,
  body: string | (() => Promise<string>),
  headers?: Record<string, string>
) => {
  headers: () => Record<string, string>;
  request: () => FixtureRequestLike;
  status: () => number;
  text: () => Promise<string>;
};

export declare const wsRecord: (sequence: number, direction: string, payloadText: string) => FixtureRecord;
