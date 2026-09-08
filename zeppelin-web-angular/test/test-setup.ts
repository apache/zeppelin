/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import 'zone.js';
// The pair src/polyfills.ts loads for the application. Without Reflect.metadata
// the emitted `__metadata` helper is a silent no-op and injection fails NG0202.
import 'core-js/es7/reflect';

import { getTestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { afterEach } from 'vitest';

getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting());

// Vitest globals are disabled, so Angular cannot install its own reset hook and
// the test module stays locked after the first spec instantiates it.
afterEach(() => getTestBed().resetTestingModule());
