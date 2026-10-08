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
import { composite, contrastRatio, readable } from './contrast';

describe('contrast', () => {
  it('measures WCAG contrast between opaque colors', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5);
    expect(contrastRatio('#3071a9', '#ffffff')).toBeCloseTo(5.17, 2);
    expect(contrastRatio('#fff', '#fff')).toBe(1);
  });

  it('paints a translucent color over its background', () => {
    expect(composite('rgba(0, 0, 0, 0.45)', '#ffffff')).toBe('#8c8c8c');
    expect(composite('#3071a9', '#141414')).toBe('#3071a9');
  });

  it('keeps a color that already passes and moves one that does not just far enough', () => {
    expect(readable('#3071a9', ['#ffffff'], 4.5, '#000000')).toBe('#3071a9');
    // antd's dark accent on the dark panel is 2.91:1.
    const lifted = readable('#2c6393', ['#141414'], 4.5, '#ffffff');
    expect(contrastRatio(lifted, '#141414')).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(lifted, '#141414')).toBeLessThan(5.5);
  });

  it('meets the minimum on every background, translucent ones over the first', () => {
    const color = readable('rgba(0, 0, 0, 0.45)', ['#ffffff', 'rgba(0, 0, 0, 0.02)'], 4.5, '#000000');
    expect(contrastRatio(color, '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(color, composite('rgba(0, 0, 0, 0.02)', '#ffffff'))).toBeGreaterThanOrEqual(4.5);
  });
});
