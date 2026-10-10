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

import { describe, expect, it } from 'vitest';
import { ReactFeatureService } from './react-feature.service';

describe('Assistant React feature flag', () => {
  const service = new ReactFeatureService();

  it('is disabled by default and enabled only by the Assistant query parameter', () => {
    expect(service.isEnabled('assistantPanel')).toBe(false);
    expect(service.isEnabled('assistantPanel', new Map([['reactFooter', 'true']]))).toBe(false);
    expect(service.isEnabled('assistantPanel', new Map([['reactAssistant', 'true']]))).toBe(true);
    expect(service.isEnabled('assistantPanel', new Map([['reactAssistant', 'false']]))).toBe(false);
  });
});
