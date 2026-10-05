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
import { DatasetType, ParagraphConfigResults, ParagraphIResultsMsgItem } from '@zeppelin/sdk';
import { SingleResultRenderer } from './SingleResultRenderer';

const result = (type: DatasetType, data: string): ParagraphIResultsMsgItem => ({ type, data });

const TABLE_DATA = 'name\tage\nalice\t30';

// Index 0 stays a table, index 1 draws a chart, so reading the wrong entry shows.
const configs = {
  0: { graph: { mode: 'table' } },
  1: { graph: { mode: 'multiBarChart' } }
} as unknown as ParagraphConfigResults;

describe('SingleResultRenderer', () => {
  it('renders TEXT as text, leaving markup in it literal', () => {
    // Markup in the payload is what separates this arm from HTML: routing TEXT to
    // HTMLRenderer would parse the tag away instead of showing it.
    render(<SingleResultRenderer index={0} result={result(DatasetType.TEXT, 'line one <b>not bold</b>')} />);

    expect(screen.getByText(/line one <b>not bold<\/b>/)).toBeTruthy();
  });

  it('renders TABLE through the visualization', () => {
    render(<SingleResultRenderer index={0} result={result(DatasetType.TABLE, TABLE_DATA)} />);

    // Only that the arm was taken. The visualization's display-mode state is a
    // known defect (projects/zeppelin-react/AGENTS.md), so nothing here pins it.
    expect(screen.getByText('alice')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Bar Chart/ })).toBeTruthy();
  });

  it('hands the visualization the display config for its own result index', () => {
    // Both indices are rendered: index 1 alone would pass against a hard-coded [1],
    // and the positive assertion keeps an absent chart from reading as success.
    const table = render(
      <SingleResultRenderer index={0} config={configs} result={result(DatasetType.TABLE, TABLE_DATA)} />
    );
    expect(screen.getByText('alice')).toBeTruthy();
    table.unmount();

    render(<SingleResultRenderer index={1} config={configs} result={result(DatasetType.TABLE, TABLE_DATA)} />);
    expect(screen.getByRole('button', { name: /Table/ })).toBeTruthy();
    expect(screen.queryByText('alice')).toBeNull();
  });

  it('renders IMG as a base64 png', () => {
    render(<SingleResultRenderer index={0} result={result(DatasetType.IMG, 'QUJD')} />);

    expect(screen.getByRole('img').getAttribute('src')).toBe('data:image/png;base64,QUJD');
  });

  it('renders SVG as an encoded image without inserting its markup into the page', () => {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg"><text>서울 # &</text><script>ignored()</script></svg>';
    const { container } = render(<SingleResultRenderer index={0} result={result(DatasetType.SVG, svg)} />);

    expect(screen.getByRole('img').getAttribute('src')).toBe(
      `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    );
    expect(container.querySelector('svg')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
  });

  it('updates the image format when an IMG result becomes SVG and back', () => {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" />';
    const view = render(<SingleResultRenderer index={0} result={result(DatasetType.IMG, 'QUJD')} />);

    view.rerender(<SingleResultRenderer index={0} result={result(DatasetType.SVG, svg)} />);
    expect(screen.getByRole('img').getAttribute('src')).toBe(
      `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    );

    view.rerender(<SingleResultRenderer index={0} result={result(DatasetType.IMG, 'REVG')} />);
    expect(screen.getByRole('img').getAttribute('src')).toBe('data:image/png;base64,REVG');
  });

  it('clears the previous image when the result becomes NULL', () => {
    const view = render(<SingleResultRenderer index={0} result={result(DatasetType.SVG, '<svg />')} />);
    expect(screen.getByRole('img')).toBeTruthy();

    view.rerender(<SingleResultRenderer index={0} result={result(DatasetType.NULL, 'ignored')} />);

    expect(screen.queryByRole('img')).toBeNull();
    expect(view.container.innerHTML).toBe('');
  });

  it('renders HTML as markup rather than as text', () => {
    render(<SingleResultRenderer index={0} result={result(DatasetType.HTML, '<p>markup output</p>')} />);

    expect(screen.getByText('markup output').tagName).toBe('P');
  });

  it('tells the user that ANGULAR results are unsupported here', () => {
    render(<SingleResultRenderer index={0} result={result(DatasetType.ANGULAR, 'anything')} />);

    expect(screen.getByText('Angular Component')).toBeTruthy();
    expect(screen.getByText(/not supported in React environment/)).toBeTruthy();
  });

  it('renders nothing for NETWORK, which has no renderer', () => {
    const { container } = render(<SingleResultRenderer index={0} result={result(DatasetType.NETWORK, 'graph')} />);

    expect(container.innerHTML).toBe('');
  });
});
