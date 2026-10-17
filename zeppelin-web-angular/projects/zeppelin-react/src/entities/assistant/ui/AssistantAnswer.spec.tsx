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

import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { AssistantAnswerView } from './answerView';
import { AssistantAnswer } from './AssistantAnswer';

const answer = () => screen.getByRole('article', { name: 'Assistant answer' });

describe('AssistantAnswer', () => {
  it('shows only the current state as the run moves from loading to done', () => {
    const { rerender } = render(<AssistantAnswer view={{ status: 'loading' }} />);
    expect(screen.getByRole('status').textContent).toBe('Thinking…');
    expect(answer().getAttribute('aria-busy')).toBe('true');

    rerender(
      <AssistantAnswer
        view={{
          status: 'streaming',
          content: 'Revenue is grouped',
          tools: [{ id: 't1', name: 'list_paragraphs', status: 'done' }]
        }}
      />
    );
    expect(screen.getByRole('status').textContent).toBe('Writing answer…');
    expect(answer().textContent).toContain('Revenue is grouped');

    rerender(
      <AssistantAnswer
        view={{
          status: 'done',
          message: {
            id: 'm1',
            role: 'assistant',
            segments: [
              { id: 's1', content: '', toolCalls: [{ id: 't1', name: 'list_paragraphs' }] },
              { id: 's2', content: 'Revenue is grouped by region.' }
            ]
          }
        }}
      />
    );
    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(answer().hasAttribute('aria-busy')).toBe(false);
    expect(answer().textContent).toContain('Revenue is grouped by region.');
    expect(screen.getByText('Used 1 tool')).toBeTruthy();
  });

  it('names the running tool in the progress line and marks each tool with its state', () => {
    render(
      <AssistantAnswer
        view={{
          status: 'streaming',
          content: '',
          tools: [
            { id: 't1', name: 'list_paragraphs', status: 'done' },
            { id: 't2', name: 'read_results', status: 'running' }
          ]
        }}
      />
    );
    expect(screen.getByRole('status').textContent).toBe('Running read_results…');
    expect(screen.getByText('Using 2 tools')).toBeTruthy();
    const [first, second] = screen.getAllByRole('listitem');
    expect(within(first).getByRole('img', { name: 'Done' })).toBeTruthy();
    expect(within(second).getByRole('img', { name: 'Running' })).toBeTruthy();
  });

  it('opens the tool log while a tool runs and collapses it when the answer completes', () => {
    const running: AssistantAnswerView = {
      status: 'streaming',
      content: '',
      tools: [{ id: 't1', name: 'list_paragraphs', status: 'running' }]
    };
    const toolLog = () => screen.getByText(/tool$/).closest('details')!;
    const { rerender } = render(<AssistantAnswer view={running} />);
    expect(toolLog().open).toBe(true);

    // A user who collapses the log keeps it collapsed while more of the answer arrives.
    toolLog().open = false;
    rerender(<AssistantAnswer view={{ ...running, content: 'Revenue' }} />);
    expect(toolLog().open).toBe(false);

    rerender(
      <AssistantAnswer
        view={{
          status: 'done',
          message: {
            id: 'm1',
            role: 'assistant',
            segments: [{ id: 's1', content: 'Revenue', toolCalls: [{ id: 't1', name: 'list_paragraphs' }] }]
          }
        }}
      />
    );
    expect(toolLog().open).toBe(false);
  });

  it('labels a tool whose completion event carried no name', () => {
    render(<AssistantAnswer view={{ status: 'streaming', content: '', tools: [{ id: 't1', status: 'running' }] }} />);
    expect(screen.getByRole('status').textContent).toBe('Running Tool…');
  });

  it('keeps completed answer rounds in order and shows tools from every round', () => {
    render(
      <AssistantAnswer
        view={{
          status: 'done',
          message: {
            id: 'm2',
            role: 'assistant',
            segments: [
              { id: 'm1', content: 'First finding', toolCalls: [{ id: 't1', name: 'list_paragraphs' }] },
              { id: 'm2', content: 'Second finding', toolCalls: [{ id: 't2', name: 'read_results' }] }
            ]
          }
        }}
      />
    );

    expect(answer().textContent).toContain('First finding\n\nSecond finding');
    expect(screen.getByText('Used 2 tools')).toBeTruthy();
    expect(screen.getAllByRole('listitem').map(item => item.textContent)).toEqual(['list_paragraphs', 'read_results']);
  });

  it('keeps the partial answer and replaces the progress line with the error', () => {
    const view: AssistantAnswerView = {
      status: 'error',
      kind: 'stream',
      message: 'The assistant stopped responding before the reply finished. Retry the request.',
      content: 'Revenue is grouped'
    };
    render(<AssistantAnswer view={view} />);
    expect(screen.queryByRole('status')).toBeNull();
    expect(answer().hasAttribute('aria-busy')).toBe(false);
    expect(answer().textContent).toContain('Revenue is grouped');
    expect(screen.getByRole('alert').textContent).toContain('stopped responding');
  });

  it('does not imply a tool is still observed after the connection is lost', () => {
    render(
      <AssistantAnswer
        view={{
          status: 'error',
          kind: 'connection',
          message: 'Connection lost',
          tools: [{ id: 't1', name: 'read_results', status: 'running' }]
        }}
      />
    );

    expect(screen.getByText('Used 1 tool (status unknown)')).toBeTruthy();
    expect(screen.queryByRole('img', { name: 'Running' })).toBeNull();
    expect(screen.getByRole('img', { name: 'Status unknown' })).toBeTruthy();
  });

  it('renders answer bodies through the given renderer', () => {
    render(
      <AssistantAnswer
        view={{
          status: 'done',
          message: { id: 'm1', role: 'assistant', segments: [{ id: 's1', content: '**bold**' }] }
        }}
        renderContent={content => <strong>{content.replaceAll('*', '')}</strong>}
      />
    );
    expect(answer().querySelector('strong')?.textContent).toBe('bold');
  });
});
