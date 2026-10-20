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

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AssistantMessageItem } from './AssistantMessageItem';

describe('AssistantMessageItem', () => {
  it('tells a question from an answer', () => {
    render(
      <>
        <AssistantMessageItem role="user" content="How is revenue calculated?" />
        <AssistantMessageItem role="assistant" content="By region." />
      </>
    );
    expect(screen.getByRole('article', { name: 'Your question' }).textContent).toBe('YouHow is revenue calculated?');
    expect(screen.getByRole('article', { name: 'Assistant answer' }).textContent).toContain('By region.');
  });

  it('shows a question as typed instead of rendering it', () => {
    render(<AssistantMessageItem role="user" content="<b>not bold</b>" renderContent={() => 'rendered'} />);
    const question = screen.getByRole('article', { name: 'Your question' });
    expect(question.textContent).toContain('<b>not bold</b>');
    expect(question.querySelector('b')).toBeNull();
  });

  it('renders an answer as Markdown by default', () => {
    render(<AssistantMessageItem role="assistant" content={'Revenue is **grouped** by `region`.'} />);
    const answer = screen.getByRole('article', { name: 'Assistant answer' });
    expect(answer.querySelector('strong')?.textContent).toBe('grouped');
    expect(answer.querySelector('code')?.textContent).toBe('region');
  });
});
