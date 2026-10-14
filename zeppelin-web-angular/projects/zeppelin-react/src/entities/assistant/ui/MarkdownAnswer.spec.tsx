/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { visuallyHidden } from '@/shared/ui/assistant-theme/AssistantScope.css';
import { MarkdownAnswer } from './MarkdownAnswer';

const paragraphId = 'paragraph_1423500782552_-1439281894';
const positiveParagraphId = 'paragraph_1423500782553_1439281894';
const describeParagraph = (id: string) =>
  id === paragraphId || id === positiveParagraphId ? { text: 'Paragraph 1', name: 'Sales query' } : undefined;

const dangerousUrls = [
  'javascript:alert%281%29',
  'JaVaScRiPt:alert%281%29',
  'java&#x73;cript:alert%281%29',
  'data:text/html,%3Cscript%3Ealert%281%29%3C/script%3E',
  'vbscript:msgbox%281%29',
  'file:///etc/passwd'
];

describe('MarkdownAnswer', () => {
  it('renders GFM table headings, cell values and column alignment', () => {
    render(
      <MarkdownAnswer
        content={
          '| Product | Region | Revenue |\n| :--- | :---: | ---: |\n| Notebook | Europe | 1200 |\n| Query | Asia | 840 |'
        }
      />
    );

    const table = screen.getByRole('table');
    const headings = within(table).getAllByRole('columnheader');
    const cells = within(table).getAllByRole('cell');
    expect(headings.map(heading => heading.textContent)).toEqual(['Product', 'Region', 'Revenue']);
    expect(cells.map(cell => cell.textContent)).toEqual(['Notebook', 'Europe', '1200', 'Query', 'Asia', '840']);
    expect(headings.map(heading => heading.style.textAlign)).toEqual(['left', 'center', 'right']);
    expect(cells.map(cell => cell.style.textAlign)).toEqual(['left', 'center', 'right', 'left', 'center', 'right']);
  });

  it('replaces old content when the answer prop changes', () => {
    const { rerender } = render(<MarkdownAnswer content={'# Old answer\n\nObsolete explanation.'} />);
    expect(screen.getByRole('heading', { name: 'Old answer' })).toBeTruthy();

    rerender(<MarkdownAnswer content={'## Updated answer\n\nFresh **results**.'} />);

    expect(screen.queryByRole('heading', { name: 'Old answer' })).toBeNull();
    expect(screen.queryByText('Obsolete explanation.')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Updated answer', level: 2 })).toBeTruthy();
    expect(screen.getByText('results').tagName).toBe('STRONG');
  });

  it('renders successive unclosed SQL fences and a following paragraph after the fence closes', () => {
    const initialSource = 'SELECT product,\n';
    const extendedSource = 'SELECT product, SUM(revenue)\nFROM sales\n';
    const completedSource = `${extendedSource}GROUP BY product;\n`;
    const { container, rerender } = render(<MarkdownAnswer content={`\`\`\`sql\n${initialSource}`} />);

    expect(screen.getByText('sql')).toBeTruthy();
    expect(container.querySelector('pre code')!.textContent).toBe(initialSource);

    rerender(<MarkdownAnswer content={`\`\`\`sql\n${extendedSource}`} />);
    expect(container.querySelector('pre code')!.textContent).toBe(extendedSource);

    rerender(<MarkdownAnswer content={`\`\`\`sql\n${completedSource}\`\`\`\n\nRevenue is grouped by product.`} />);
    expect(container.querySelector('pre code')!.textContent).toBe(completedSource);
    expect(screen.getByText('Revenue is grouped by product.').tagName).toBe('P');
  });

  it.each([
    { language: '', label: 'Code' },
    { language: 'unknown-lang', label: 'unknown-lang' }
  ])('preserves source text for a "$language" code fence', ({ language, label }) => {
    const source = '<widget onclick="run()"> & value\n  keep indentation\n';
    const { container } = render(<MarkdownAnswer content={`\`\`\`${language}\n${source}\`\`\``} />);

    expect(screen.getByText(label)).toBeTruthy();
    expect(container.querySelector('pre code')!.textContent).toBe(source);
    expect(container.querySelector('widget')).toBeNull();
  });

  it.each([
    { form: 'ordinary text', content: `See ${paragraphId} for the query.` },
    { form: 'inline code', content: `See \`${paragraphId}\` for the query.` },
    { form: 'explicit paragraph link', content: `[Open the query](#${paragraphId})` },
    { form: 'positive suffix', content: `See ${positiveParagraphId}.`, id: positiveParagraphId }
  ])('opens a known paragraph from $form without creating a navigation link', ({ content, id = paragraphId }) => {
    const onOpenParagraph = vi.fn();
    render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={onOpenParagraph} />
    );

    const button = screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' });
    expect(button.textContent).toBe('Paragraph 1');
    expect(button.getAttribute('type')).toBe('button');
    expect(screen.queryByRole('link')).toBeNull();

    fireEvent.click(button);
    expect(onOpenParagraph).toHaveBeenCalledExactlyOnceWith(id, 'Sales query');
  });

  it('uses the latest paragraph callback when the answer content is unchanged', () => {
    const content = `See ${paragraphId}.`;
    const originalCallback = vi.fn();
    const updatedCallback = vi.fn();
    const { rerender } = render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={originalCallback} />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' }));
    expect(originalCallback).toHaveBeenCalledExactlyOnceWith(paragraphId, 'Sales query');

    rerender(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={updatedCallback} />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' }));
    expect(updatedCallback).toHaveBeenCalledExactlyOnceWith(paragraphId, 'Sales query');
    expect(originalCallback).toHaveBeenCalledTimes(1);
  });

  it('updates paragraph labels and availability without changing the answer content', () => {
    const content = `See ${paragraphId}.`;
    const onOpenParagraph = vi.fn();
    const { container, rerender } = render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={onOpenParagraph} />
    );
    expect(screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' }).textContent).toBe(
      'Paragraph 1'
    );

    rerender(
      <MarkdownAnswer
        content={content}
        describeParagraph={() => ({ text: 'Updated paragraph', name: 'Updated query' })}
        onOpenParagraph={onOpenParagraph}
      />
    );

    expect(screen.queryByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' })).toBeNull();
    const button = screen.getByRole('button', { name: 'Updated paragraph: show Updated query in the notebook' });
    expect(button.textContent).toBe('Updated paragraph');
    fireEvent.click(button);
    expect(onOpenParagraph).toHaveBeenCalledExactlyOnceWith(paragraphId, 'Updated query');

    rerender(
      <MarkdownAnswer content={content} describeParagraph={() => undefined} onOpenParagraph={onOpenParagraph} />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(container.textContent).toBe(`See ${paragraphId}.`);
  });

  it('leaves fenced references and ordinary link labels untouched', () => {
    const source = `${paragraphId}\n`;
    const { container } = render(
      <MarkdownAnswer
        content={`\`\`\`text\n${source}\`\`\`\n\n[${paragraphId}](https://example.com/query)`}
        describeParagraph={describeParagraph}
        onOpenParagraph={vi.fn()}
      />
    );

    expect(container.querySelector('pre code')!.textContent).toBe(source);
    expect(screen.getByRole('link', { name: paragraphId }).getAttribute('href')).toBe('https://example.com/query');
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('does not rewrite partial or numbered IDs, malformed IDs or larger inline-code values', () => {
    const content = [
      'Paragraph #3 and paragraph 3',
      'paragraph_1',
      'paragraph_1423500782552_',
      'paragraph_1423500782552_-',
      'paragraph_1423500782552_--1439281894',
      `prefix${paragraphId}`,
      `${paragraphId}suffix`,
      `\`before ${paragraphId} after\``
    ].join('\n\n');
    const { container } = render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={vi.fn()} />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(container.textContent).toContain('paragraph_1');
    expect(container.textContent).toContain('paragraph_1423500782552_--1439281894');
    expect(container.textContent).toContain(`prefix${paragraphId}`);
    expect(container.textContent).toContain(`${paragraphId}suffix`);
    expect(screen.getByText(`before ${paragraphId} after`).tagName).toBe('CODE');
  });

  it('does not turn parts of Unicode identifiers into paragraph references', () => {
    const content = [
      `𐐀${paragraphId}`,
      `${paragraphId}𐐀`,
      `𝟙${paragraphId}`,
      `${paragraphId}𝟙`,
      `e\u0301${paragraphId}`,
      `${paragraphId}\u0301`
    ].join(' ');
    const { container } = render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={vi.fn()} />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(container.textContent).toBe(content);
  });

  it('preserves punctuation around adjacent references and reports each paragraph ID', () => {
    const onOpenParagraph = vi.fn();
    const { container } = render(
      <MarkdownAnswer
        content={`See 🧪(${paragraphId}),${positiveParagraphId}; done.`}
        describeParagraph={describeParagraph}
        onOpenParagraph={onOpenParagraph}
      />
    );

    expect(container.textContent).toBe('See 🧪(Paragraph 1),Paragraph 1; done.');
    const buttons = screen.getAllByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' });
    fireEvent.click(buttons[0]);
    fireEvent.click(buttons[1]);
    expect(onOpenParagraph).toHaveBeenNthCalledWith(1, paragraphId, 'Sales query');
    expect(onOpenParagraph).toHaveBeenNthCalledWith(2, positiveParagraphId, 'Sales query');
  });

  it('handles references inside nested Markdown and tables without rewriting source code or reference-link labels', () => {
    const onOpenParagraph = vi.fn();
    const source = `${paragraphId} ${positiveParagraphId}\n`;
    const content = [
      `> - **${paragraphId}**`,
      '',
      '| Query | Revenue |',
      '| --- | ---: |',
      `| \`${positiveParagraphId}\` | 1200 |`,
      '',
      `[${paragraphId}][source]`,
      '',
      '[source]: https://example.com/query',
      '',
      `\`\`\`text\n${source}\`\`\``
    ].join('\n');
    const { container } = render(
      <MarkdownAnswer content={content} describeParagraph={describeParagraph} onOpenParagraph={onOpenParagraph} />
    );

    fireEvent.click(
      within(screen.getByRole('list')).getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' })
    );
    fireEvent.click(
      within(screen.getByRole('table')).getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' })
    );
    expect(onOpenParagraph).toHaveBeenNthCalledWith(1, paragraphId, 'Sales query');
    expect(onOpenParagraph).toHaveBeenNthCalledWith(2, positiveParagraphId, 'Sales query');
    expect(container.querySelector('pre code')!.textContent).toBe(source);
    expect(screen.getByRole('link', { name: paragraphId }).getAttribute('href')).toBe('https://example.com/query');
  });

  it('keeps paragraph descriptions and callbacks isolated between answers', () => {
    const salesCallback = vi.fn();
    const inventoryCallback = vi.fn();
    render(
      <>
        <MarkdownAnswer content={paragraphId} describeParagraph={describeParagraph} onOpenParagraph={salesCallback} />
        <MarkdownAnswer
          content={paragraphId}
          describeParagraph={() => ({ text: 'Paragraph 2', name: 'Inventory query' })}
          onOpenParagraph={inventoryCallback}
        />
      </>
    );

    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 1: show Sales query in the notebook' }));
    expect(salesCallback).toHaveBeenCalledExactlyOnceWith(paragraphId, 'Sales query');
    expect(inventoryCallback).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Paragraph 2: show Inventory query in the notebook' }));
    expect(inventoryCallback).toHaveBeenCalledExactlyOnceWith(paragraphId, 'Inventory query');
    expect(salesCallback).toHaveBeenCalledTimes(1);
  });

  it('keeps unknown paragraph references inactive without disabling ordinary anchors', () => {
    const unknownId = 'paragraph_1423500782554_-99';
    const { container } = render(
      <MarkdownAnswer
        content={`${unknownId}\n\n\`${unknownId}\`\n\n[Unknown query](#${unknownId})\n\n[Other section](#section)`}
        describeParagraph={describeParagraph}
        onOpenParagraph={vi.fn()}
      />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Unknown query' })).toBeNull();
    const anchor = screen.getByRole('link', { name: 'Other section' });
    expect(anchor.getAttribute('href')).toBe('#section');
    expect(anchor.getAttribute('target')).toBeNull();
    expect(container.textContent).toContain(unknownId);
    expect(screen.getByText('Unknown query')).toBeTruthy();
    expect(screen.getByText('Other section')).toBeTruthy();
  });

  it('leaves known references as text when the paragraph callback is absent', () => {
    const { container } = render(
      <MarkdownAnswer
        content={`${paragraphId}\n\n\`${paragraphId}\`\n\n[Open the query](#${paragraphId})`}
        describeParagraph={describeParagraph}
      />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
    expect(container.textContent).toContain(paragraphId);
    expect(screen.getByText('Open the query')).toBeTruthy();
  });

  it('leaves references as text when no paragraph description is available', () => {
    const { container } = render(
      <MarkdownAnswer content={`${paragraphId}\n\n[Open the query](#${paragraphId})`} onOpenParagraph={vi.fn()} />
    );

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
    expect(container.textContent).toContain(paragraphId);
    expect(screen.getByText('Open the query')).toBeTruthy();
  });

  it('escapes raw HTML including scripts, embedded content and event attributes', () => {
    const payload = [
      '<script>alert("unsafe")</script>',
      '<img src="https://example.com/tracker.png" onerror="alert(1)">',
      '<svg onload="alert(1)"><circle /></svg>',
      '<iframe src="https://example.com/frame"></iframe>',
      '<button onclick="alert(1)">Run</button>'
    ].join('\n\n');
    const { container } = render(<MarkdownAnswer content={payload} />);

    expect(container.textContent).toContain('<script>alert("unsafe")</script>');
    expect(container.textContent).toContain('onerror="alert(1)"');
    expect(container.textContent).toContain('onload="alert(1)"');
    expect(container.textContent).toContain('onclick="alert(1)"');
    expect(container.querySelector('script, img, svg, iframe, button')).toBeNull();
    expect(container.querySelector('[onclick], [onerror], [onload]')).toBeNull();
  });

  describe('footnotes', () => {
    const content = 'Revenue grew[^1].\n\n[^1]: Source: finance.';
    const renderTwo = () =>
      render(
        <>
          <MarkdownAnswer content={content} />
          <MarkdownAnswer content={content} />
        </>
      );

    it('gives every element of two answers its own id, and points each reference at its own heading', () => {
      const { container } = renderTwo();

      const ids = Array.from(container.querySelectorAll('[id]')).map(element => element.id);
      expect(ids.length).toBeGreaterThan(0);
      expect(new Set(ids).size).toBe(ids.length);
      const references = Array.from(container.querySelectorAll('a[data-footnote-ref]'));
      expect(references).toHaveLength(2);
      for (const reference of references) {
        const heading = container.querySelector(`[id="${reference.getAttribute('aria-describedby')}"]`);
        expect(heading?.textContent).toBe('Footnotes');
        expect(heading?.closest('section')?.contains(reference)).toBe(false);
      }
      expect(references[0].getAttribute('aria-describedby')).not.toBe(references[1].getAttribute('aria-describedby'));
    });

    it('hides the heading visually, and shows a footnote and moves focus to it without changing the URL hash', () => {
      const { container } = renderTwo();

      expect(screen.getAllByText('Footnotes')[0].className).toBe(visuallyHidden);
      const [reference] = Array.from(container.querySelectorAll('a[data-footnote-ref]'));
      const target = container.querySelector(`[id="${reference.getAttribute('href')!.slice(1)}"]`) as HTMLElement;
      const scrollIntoView = vi.fn();
      target.scrollIntoView = scrollIntoView;
      const hash = window.location.hash;

      expect(fireEvent.click(reference)).toBe(false);

      expect(scrollIntoView).toHaveBeenCalledOnce();
      expect(document.activeElement).toBe(target);
      expect(window.location.hash).toBe(hash);
    });
  });

  it.each(dangerousUrls)('renders the rejected URL %s as non-navigable text', url => {
    const { container } = render(<MarkdownAnswer content={`[Unsafe link](${url})`} />);

    expect(screen.getByText('Unsafe link')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
    expect(container.querySelector('a, [href]')).toBeNull();
  });

  it('preserves safe HTTPS links with a protected new-tab target', () => {
    render(<MarkdownAnswer content={'[Zeppelin documentation](https://zeppelin.apache.org/docs/latest/)'} />);

    const link = screen.getByRole('link', { name: 'Zeppelin documentation' });
    expect(link.getAttribute('href')).toBe('https://zeppelin.apache.org/docs/latest/');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')?.split(/\s+/)).toEqual(expect.arrayContaining(['noopener', 'noreferrer']));
  });
});
