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

import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Tests drive React roots directly (the Module Federation mount contract),
// so opt into act()-aware scheduling globally.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Vitest globals are disabled, so Testing Library cannot self-register its
// auto-cleanup hook; without this, rendered DOM leaks between tests.
afterEach(cleanup);
