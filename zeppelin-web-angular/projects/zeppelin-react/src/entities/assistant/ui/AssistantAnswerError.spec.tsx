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

import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AssistantConnectionError, AssistantStreamError } from '../model/assistantContract';
import { AssistantAnswerError } from './AssistantAnswerError';

describe('AssistantAnswerError', () => {
  it('shows a server failure with a retry action', () => {
    const onRetry = vi.fn();
    render(<AssistantAnswerError kind="failed" message="The model request failed." onRetry={onRetry} />);
    expect(screen.getByRole('alert').textContent).toContain('The model request failed.');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('offers retry when the stream ended early', () => {
    render(<AssistantAnswerError kind="stream" message={new AssistantStreamError().message} onRetry={() => {}} />);
    expect(screen.getByRole('alert').textContent).toContain('stopped responding before the reply finished');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
  });

  it('asks for a reload, not a retry, when the connection dropped', () => {
    const onRetry = vi.fn();
    render(
      <AssistantAnswerError kind="connection" message={new AssistantConnectionError().message} onRetry={onRetry} />
    );
    expect(screen.getByRole('alert').textContent).toContain('connection to the server was lost');
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Reload conversation' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('shows no button when there is nothing to retry', () => {
    render(<AssistantAnswerError kind="failed" message="The model request failed." />);
    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });
});
