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

import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { HTMLRenderer } from './HTMLRenderer';

// A file of its own: the mock is hoisted over the whole module, so it cannot
// share one with the specs that need highlighting to work.
vi.mock('highlight.js', () => {
  throw new Error('chunk load failed');
});

describe('HTMLRenderer when the highlight chunk fails', () => {
  it('leaves the code block unhighlighted instead of raising an unhandled rejection', async () => {
    const { container } = render(<HTMLRenderer html="<pre><code>const x = 1;</code></pre>" />);

    // Give the rejected import a turn to settle; nothing must escape it.
    await vi.waitFor(() => expect(container.querySelector('pre code')).not.toBeNull());

    expect(container.querySelector('pre code')!.classList.contains('hljs')).toBe(false);
    expect(container.textContent).toContain('const x = 1;');
  });
});
