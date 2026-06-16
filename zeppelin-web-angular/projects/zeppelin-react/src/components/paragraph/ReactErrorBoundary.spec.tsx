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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReactErrorBoundary } from './ReactErrorBoundary';

const Bomb = (): never => {
  throw new Error('boom');
};

describe('ReactErrorBoundary', () => {
  beforeEach(() => {
    // React logs caught boundary errors via console.error in development;
    // silence it so test output stays readable.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders children when nothing throws', () => {
    const onError = vi.fn();
    const { container } = render(
      <ReactErrorBoundary onError={onError}>
        <div data-testid="child">ok</div>
      </ReactErrorBoundary>
    );

    expect(container.querySelector('[data-testid="child"]')).not.toBeNull();
    expect(onError).not.toHaveBeenCalled();
  });

  it('renders nothing and reports the error once when a child throws during render', () => {
    const onError = vi.fn();
    const { container } = render(
      <ReactErrorBoundary onError={onError}>
        <Bomb />
      </ReactErrorBoundary>
    );

    expect(container.innerHTML).toBe('');
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: 'boom' }));
  });

  it('swallows errors thrown by the onError callback itself', () => {
    const onError = vi.fn(() => {
      throw new Error('callback exploded');
    });

    expect(() =>
      render(
        <ReactErrorBoundary onError={onError}>
          <Bomb />
        </ReactErrorBoundary>
      )
    ).not.toThrow();
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('does not crash when no onError is provided', () => {
    expect(() =>
      render(
        <ReactErrorBoundary>
          <Bomb />
        </ReactErrorBoundary>
      )
    ).not.toThrow();
  });

  it('does not recover after an error: healthy re-renders still produce nothing', () => {
    const onError = vi.fn();
    const { container, rerender } = render(
      <ReactErrorBoundary onError={onError}>
        <Bomb />
      </ReactErrorBoundary>
    );
    expect(container.innerHTML).toBe('');

    rerender(
      <ReactErrorBoundary onError={onError}>
        <div data-testid="healthy">ok</div>
      </ReactErrorBoundary>
    );

    // hasError never resets; recovery requires unmount + remount,
    // which is exactly what the Angular host does via the fallback branch.
    expect(container.innerHTML).toBe('');
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
