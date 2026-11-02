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

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HTMLRenderer } from './HTMLRenderer';

// jsdom never runs scripts, so the execution this component exists for is out of reach here and belongs in e2e.
// What these specs pin is the fidelity of the swap:
// attributes, body and position survive it. They do not prove the swap happened,
// because a script parsed out of innerHTML already carries all three.
// Only the async assertion below distinguishes a rebuilt script from an inert one.
describe('HTMLRenderer', () => {
  it('renders the markup it is given', () => {
    render(<HTMLRenderer html="<p>rendered output</p>" />);

    expect(screen.getByText('rendered output')).toBeTruthy();
  });

  it('carries the original attributes onto the replacement script', () => {
    const { container } = render(
      <HTMLRenderer html='<script type="text/javascript" data-mark="kept" src="lib.js"></script>' />
    );

    const script = container.querySelector('script')!;
    expect(script.getAttribute('type')).toBe('text/javascript');
    expect(script.getAttribute('data-mark')).toBe('kept');
    expect(script.getAttribute('src')).toBe('lib.js');
  });

  it('keeps the script body so the replacement has something to run', () => {
    const { container } = render(<HTMLRenderer html="<script>window.answer = 42;</script>" />);

    expect(container.querySelector('script')!.textContent).toBe('window.answer = 42;');
  });

  it('forces async off even when the source markup asked for it', () => {
    // A library and the code using it must not arrive out of order.
    const { container } = render(<HTMLRenderer html='<script async src="lib.js"></script>' />);

    expect(container.querySelector('script')!.async).toBe(false);
  });

  it('leaves each script where it was among the surrounding markup', () => {
    const { container } = render(
      <HTMLRenderer html='<p>before</p><script id="one"></script><p>between</p><script id="two"></script>' />
    );

    const ids = Array.from(container.querySelectorAll('.inner-html > *')).map(node => node.id || node.tagName);
    expect(ids).toEqual(['P', 'one', 'P', 'two']);
  });

  it('highlights a code block, matching what the Angular renderer does', async () => {
    const { container } = render(<HTMLRenderer html="<pre><code>const x = 1;</code></pre>" />);

    // highlight.js arrives through a dynamic import, so the class lands a tick
    // later. Which language it guesses is its own business and not pinned here.
    await waitFor(() => expect(container.querySelector('pre code')!.classList.contains('hljs')).toBe(true));
  });

  it('replaces the previous output when the html changes', () => {
    const { rerender } = render(<HTMLRenderer html="<p>first</p>" />);

    rerender(<HTMLRenderer html="<p>second</p>" />);

    expect(screen.queryByText('first')).toBeNull();
    expect(screen.getByText('second')).toBeTruthy();
  });

  it('dismisses only result warnings, including after the HTML is replaced', () => {
    const html =
      '<div class="result-alert alert-warning" role="alert"><button class="close" data-dismiss="alert" aria-label="Close"><span>&times;</span></button>102400 bytes</div><button class="close" data-dismiss="alert">Ordinary output</button>';
    const { rerender } = render(<HTMLRenderer html={html} />);

    fireEvent.click(screen.getByRole('button', { name: 'Ordinary output' }));
    expect(screen.getByRole('alert').textContent).toContain('102400 bytes');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }).firstChild!);
    rerender(<HTMLRenderer html={html} />);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('button', { name: 'Ordinary output' })).toBeTruthy();

    rerender(<HTMLRenderer html={html.replace('102400 bytes', '1000 rows')} />);
    expect(screen.getByRole('alert').textContent).toContain('1000 rows');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('renders nothing visible for empty html', () => {
    const { container } = render(<HTMLRenderer html="" />);

    expect(container.textContent).toBe('');
  });
});
