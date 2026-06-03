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
import { mount, ParagraphFooterMountHandle, ParagraphFooterProps } from './ParagraphFooter';

const baseProps: ParagraphFooterProps = {
  dateStarted: '2026-06-03T10:00:00.000Z',
  dateFinished: '2026-06-03T10:00:05.000Z',
  dateUpdated: '2026-06-03T09:59:00.000Z',
  showExecutionTime: true,
  showElapsedTime: false,
  user: 'alice'
};

describe('ParagraphFooter mount contract', () => {
  let host: HTMLElement | null = null;
  let handle: ParagraphFooterMountHandle | null = null;

  const mountFooter = (props: ParagraphFooterProps): void => {
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
  });

  it('throws when no element is given', () => {
    expect(() => mount(null as unknown as HTMLElement, baseProps)).toThrow('Mount element is required');
  });

  it('returns an update/unmount handle and renders the execution time', () => {
    mountFooter(baseProps);

    expect(typeof handle!.update).toBe('function');
    expect(typeof handle!.unmount).toBe('function');

    const executionTime = host!.querySelector('.execution-time');
    expect(executionTime).not.toBeNull();
    expect(executionTime!.textContent).toContain('Took 5 seconds. Last updated by alice at');
    expect(host!.querySelector('.elapsed-time')).toBeNull();
  });

  it('falls back to "anonymous" when no user is given', () => {
    mountFooter({ ...baseProps, user: undefined });

    expect(host!.querySelector('.execution-time')!.textContent).toContain('Last updated by anonymous at');
  });

  it('appends (outdated) when the paragraph changed after the run started', () => {
    mountFooter({ ...baseProps, dateUpdated: '2026-06-03T10:00:01.000Z' });

    expect(host!.querySelector('.execution-time')!.textContent).toMatch(/\(outdated\)$/);
  });

  it('renders bare "outdated" when the duration is invalid but the paragraph is stale', () => {
    mountFooter({
      ...baseProps,
      dateFinished: '2026-06-03T09:00:00.000Z', // finished before started
      dateUpdated: '2026-06-03T10:00:01.000Z'
    });

    expect(host!.querySelector('.execution-time')!.textContent).toBe('outdated');
  });

  it('renders the elapsed time since dateStarted while running', () => {
    // Fake only Date so React's scheduler timers keep working
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-06-03T10:05:00.000Z') });
    try {
      mountFooter({ ...baseProps, showExecutionTime: false, showElapsedTime: true });

      expect(host!.querySelector('.execution-time')).toBeNull();
      expect(host!.querySelector('.elapsed-time')!.textContent).toBe('Started 5 minutes ago.');
    } finally {
      vi.useRealTimers();
    }
  });

  it('update() re-renders in place with new props', () => {
    mountFooter(baseProps);
    expect(host!.querySelector('.execution-time')).not.toBeNull();

    const h = handle!;
    act(() => h.update({ ...baseProps, showExecutionTime: false }));

    expect(host!.querySelector('.execution-time')).toBeNull();
    expect(host!.querySelector('[data-testid="react-paragraph-footer-content"]')).not.toBeNull();
  });

  it('unmount() empties the host element', () => {
    mountFooter(baseProps);
    const h = handle!;
    handle = null;

    act(() => h.unmount());

    expect(host!.innerHTML).toBe('');
  });
});
