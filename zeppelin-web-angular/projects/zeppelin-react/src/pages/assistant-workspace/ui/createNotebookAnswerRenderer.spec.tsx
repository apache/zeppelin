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
import { createNotebookAnswerRenderer } from './createNotebookAnswerRenderer';

const firstId = 'paragraph_1423500782552_-1439281894';
const secondId = 'paragraph_1423500782553_1439281894';

describe('createNotebookAnswerRenderer', () => {
  it.each([
    { title: '  Sales query  ', name: 'Sales query' },
    { title: '   ', name: 'Paragraph 1' }
  ])('renders known references with $name and leaves unknown references as text', ({ title, name }) => {
    const onOpenParagraph = vi.fn();
    const renderAnswer = createNotebookAnswerRenderer([{ id: firstId, title }], onOpenParagraph);
    render(renderAnswer(`See ${firstId} and ${secondId}.`));

    const reference = screen.getByRole('button', { name: `Paragraph 1: show ${name} in the notebook` });
    expect(reference.textContent).toBe('Paragraph 1');
    expect(screen.getByText(new RegExp(secondId))).toBeTruthy();
    expect(screen.getAllByRole('button')).toHaveLength(1);
    fireEvent.click(reference);
    expect(onOpenParagraph).toHaveBeenCalledExactlyOnceWith(firstId);
  });

  it('uses current paragraph order, titles and navigation when the notebook changes', () => {
    const previousNavigation = vi.fn();
    const content = `${firstId} and ${secondId}`;
    const initialRenderer = createNotebookAnswerRenderer(
      [
        { id: firstId, title: 'Old query' },
        { id: secondId, title: 'Second query' }
      ],
      previousNavigation
    );
    const { rerender } = render(initialRenderer(content));
    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 2: show Second query in the notebook' }));
    expect(previousNavigation).toHaveBeenCalledExactlyOnceWith(secondId);

    const currentNavigation = vi.fn();
    const currentRenderer = createNotebookAnswerRenderer([{ id: secondId, title: 'Updated query' }], currentNavigation);
    rerender(currentRenderer(content));

    expect(screen.getByText(new RegExp(firstId))).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Old query|Second query/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 1: show Updated query in the notebook' }));
    expect(currentNavigation).toHaveBeenCalledExactlyOnceWith(secondId);
    expect(previousNavigation).toHaveBeenCalledTimes(1);
  });
});
