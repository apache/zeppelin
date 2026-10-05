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
import { afterEach, describe, expect, it } from 'vitest';
import {
  ConfigurationEntry,
  ConfigurationTableMountHandle,
  ConfigurationTableProps,
  mount
} from './ConfigurationTable';

const entries: ConfigurationEntry[] = [
  ['zeppelin.server.addr', '127.0.0.1'],
  ['zeppelin.server.port', '8080']
];

// antd renders its "No data" placeholder as a row, so data rows are the rest.
const rowTexts = (host: HTMLElement): string[][] =>
  Array.from(host.querySelectorAll('tbody tr:not(.ant-table-placeholder)')).map(row =>
    Array.from(row.querySelectorAll('td')).map(cell => cell.textContent ?? '')
  );

describe('ConfigurationTable mount contract', () => {
  let host: HTMLElement | null = null;
  let handle: ConfigurationTableMountHandle | null = null;

  const mountTable = (props: ConfigurationTableProps): void => {
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
    expect(() => mount(null as unknown as HTMLElement, { entries })).toThrow('Mount element is required');
  });

  it('returns an update/unmount handle and renders one row per entry', () => {
    mountTable({ entries });

    expect(typeof handle!.update).toBe('function');
    expect(typeof handle!.unmount).toBe('function');

    const headers = Array.from(host!.querySelectorAll('thead th')).map(th => th.textContent);
    expect(headers).toEqual(['Name', 'Value']);
    expect(rowTexts(host!)).toEqual([
      ['zeppelin.server.addr', '127.0.0.1'],
      ['zeppelin.server.port', '8080']
    ]);
  });

  it('keeps the order the host passed in', () => {
    // The shell sorts by name before handing the entries over, so the remote
    // must not impose its own ordering.
    mountTable({ entries: [...entries].reverse() });

    expect(rowTexts(host!).map(([name]) => name)).toEqual(['zeppelin.server.port', 'zeppelin.server.addr']);
  });

  it('shows the empty placeholder when the host has no entries yet', () => {
    mountTable({});

    expect(host!.querySelector('[data-testid="configuration-table"]')).not.toBeNull();
    expect(host!.querySelector('.ant-table-placeholder')).not.toBeNull();
    expect(rowTexts(host!)).toEqual([]);
  });

  it('update() re-renders in place with new entries', () => {
    mountTable({ entries });

    const h = handle!;
    act(() => h.update({ entries: [['zeppelin.war', 'zeppelin-web/dist']] }));

    expect(rowTexts(host!)).toEqual([['zeppelin.war', 'zeppelin-web/dist']]);
  });

  it('unmount() empties the host element', () => {
    mountTable({ entries });
    const h = handle!;
    handle = null;

    act(() => h.unmount());

    expect(host!.innerHTML).toBe('');
  });
});
