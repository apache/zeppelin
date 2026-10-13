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
import { AssistantComposer, type AssistantComposerProps } from './AssistantComposer';

const renderComposer = (props: Partial<AssistantComposerProps> = {}) => {
  const onChange = vi.fn();
  const onSubmit = vi.fn();
  render(<AssistantComposer value="" onChange={onChange} onSubmit={onSubmit} {...props} />);
  return { onChange, onSubmit };
};

const input = () => screen.getByRole('textbox', { name: 'Question' }) as HTMLTextAreaElement;
const send = () => screen.getByRole('button', { name: /Send/ }) as HTMLButtonElement;
const pressEnter = (init: Partial<KeyboardEventInit> = {}) => fireEvent.keyDown(input(), { key: 'Enter', ...init });

describe('AssistantComposer', () => {
  it('passes typed text to the parent', () => {
    const { onChange } = renderComposer();
    fireEvent.change(input(), { target: { value: 'Explain this notebook' } });
    expect(onChange).toHaveBeenCalledWith('Explain this notebook');
  });

  it('sends the trimmed question from the button and from Enter', () => {
    const { onSubmit } = renderComposer({ value: '  Explain this notebook\n' });
    fireEvent.click(send());
    pressEnter();
    expect(onSubmit.mock.calls).toEqual([['Explain this notebook'], ['Explain this notebook']]);
  });

  it.each([
    ['empty', ''],
    ['whitespace only', ' \n\t ']
  ])('does not send %s input', (_, value) => {
    const { onSubmit } = renderComposer({ value });
    expect(send().disabled).toBe(true);
    pressEnter();
    fireEvent.submit(input().form!);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('does not send while read-only and says why', () => {
    const { onSubmit } = renderComposer({
      value: 'Explain this notebook',
      readOnly: true,
      readOnlyReason: 'This conversation belongs to another user.'
    });
    expect(input().disabled).toBe(true);
    expect(send().disabled).toBe(true);
    expect(screen.getByRole('note').textContent).toBe('This conversation belongs to another user.');
    fireEvent.submit(input().form!);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('keeps the question visible but does not send it again while sending', () => {
    const { onSubmit } = renderComposer({ value: 'Explain this notebook', sending: true });
    expect(input().readOnly).toBe(true);
    expect(input().value).toBe('Explain this notebook');
    expect(send().disabled).toBe(true);
    expect(screen.queryByRole('note')).toBeNull();
    pressEnter();
    fireEvent.submit(input().form!);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('leaves Shift+Enter and Enter that confirms an IME composition to the input', () => {
    const { onSubmit } = renderComposer({ value: '질문' });
    expect(pressEnter({ shiftKey: true })).toBe(true);
    expect(pressEnter({ isComposing: true })).toBe(true);
    expect(onSubmit).not.toHaveBeenCalled();
    expect(pressEnter()).toBe(false);
    expect(onSubmit).toHaveBeenCalledWith('질문');
  });
});
