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

import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DatasetType } from '@zeppelin/sdk';
import { mount, PublishedParagraphMountHandle, PublishedParagraphProps } from './PublishedParagraph';

const textResult = (data: string) => ({ type: DatasetType.TEXT, data });

const baseProps: PublishedParagraphProps = {
  paragraphId: 'paragraph-1',
  results: [textResult('first output')]
};

describe('PublishedParagraph mount contract', () => {
  let host: HTMLElement | null = null;
  let handle: PublishedParagraphMountHandle | null = null;

  const mountParagraph = (props?: PublishedParagraphProps): void => {
    host = document.createElement('div');
    document.body.appendChild(host);
    act(() => {
      handle = mount(host as HTMLElement, props);
    });
  };

  afterEach(() => {
    if (handle) {
      const h = handle;
      act(() => h.unmount());
      handle = null;
    }
    host?.remove();
    host = null;
    vi.restoreAllMocks();
  });

  it('throws when no element is given', () => {
    expect(() => mount(null as unknown as HTMLElement, baseProps)).toThrow('Mount element is required');
  });

  it('returns an update/unmount handle and renders the results', () => {
    mountParagraph(baseProps);

    expect(typeof handle!.update).toBe('function');
    expect(typeof handle!.unmount).toBe('function');

    const rendered = host!.querySelector('[data-testid="react-published-paragraph"]');
    expect(rendered).not.toBeNull();
    expect(rendered!.textContent).toContain('first output');
  });

  it('renders the empty state when the paragraph has no results', () => {
    mountParagraph({ ...baseProps, results: [] });

    expect(host!.querySelector('[data-testid="react-published-paragraph"]')).toBeNull();
    expect(host!.textContent).toContain('No paragraph data found');
  });

  it('renders the empty state when mounted without props at all', () => {
    mountParagraph();

    expect(host!.textContent).toContain('No paragraph data found');
  });

  it('update() re-renders in place with new results', () => {
    mountParagraph(baseProps);
    const rootBefore = host!.firstElementChild;

    const h = handle!;
    act(() => h.update({ ...baseProps, results: [textResult('second output')] }));

    const rendered = host!.querySelector('[data-testid="react-published-paragraph"]')!;
    expect(rendered.textContent).toContain('second output');
    expect(rendered.textContent).not.toContain('first output');
    // Same host subtree, not a remount. In-place update, which is what the host relies on.
    expect(host!.firstElementChild).toBe(rootBefore);
  });

  it('update() can move the paragraph back to the empty state', () => {
    mountParagraph(baseProps);

    const h = handle!;
    act(() => h.update({ ...baseProps, results: [] }));

    expect(host!.querySelector('[data-testid="react-published-paragraph"]')).toBeNull();
    expect(host!.textContent).toContain('No paragraph data found');
  });

  it('unmount() empties the host element', () => {
    mountParagraph(baseProps);
    const h = handle!;
    handle = null;

    act(() => h.unmount());

    expect(host!.innerHTML).toBe('');
  });

  it('reports render failures through onError instead of throwing at the host', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const onError = vi.fn();

    // Truthy with a length but not an array, so results.map() throws during render.
    const malformed = { length: 1 } as unknown as PublishedParagraphProps['results'];

    expect(() => mountParagraph({ ...baseProps, results: malformed, onError })).not.toThrow();

    expect(host!.innerHTML).toBe('');
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
