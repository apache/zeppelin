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

  it('keeps mode selection and exports available outside published mode', () => {
    render(<SingleResultRenderer index={0} result={result(DatasetType.TABLE, TABLE_DATA)} />);

    expect(screen.getByRole('table')).toBeTruthy();
    expect(
      screen.getAllByRole('button', { name: /\b(Table|Bar Chart|Pie Chart|Line Chart|Area Chart|Scatter Chart)$/ })
    ).toHaveLength(6);
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Export Excel' })).toBeTruthy();
  });

  it('renders the host-selected mode for each result and follows config updates', () => {
    const view = render(
      <SingleResultRenderer index={0} config={configs} result={result(DatasetType.TABLE, TABLE_DATA)} published />
    );
    expect(screen.getByRole('table')).toBeTruthy();
    expect(screen.getByText('alice')).toBeTruthy();

    view.rerender(
      <SingleResultRenderer index={1} config={configs} result={result(DatasetType.TABLE, TABLE_DATA)} published />
    );
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByText('alice')).toBeNull();

    view.rerender(
      <SingleResultRenderer index={0} config={configs} result={result(DatasetType.TABLE, TABLE_DATA)} published />
    );
    expect(screen.getByRole('table')).toBeTruthy();
    expect(screen.getByText('alice')).toBeTruthy();
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
