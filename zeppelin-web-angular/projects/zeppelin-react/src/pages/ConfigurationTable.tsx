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

import { createRoot, Root } from 'react-dom/client';
import { Table } from 'antd';
import { ReactErrorBoundary } from '@/components';
import { ZeppelinThemeProvider } from '@/theme';

/** One `[name, value]` pair as the shell's ConfigurationService hands it over. */
export type ConfigurationEntry = [string, string];

export interface ConfigurationTableProps {
  entries?: ConfigurationEntry[];
  onError?: (error: unknown) => void;
}

interface ConfigurationRow {
  key: string;
  name: string;
  value: string;
}

const COLUMNS = [
  { title: 'Name', dataIndex: 'name', key: 'name' },
  { title: 'Value', dataIndex: 'value', key: 'value' }
];

// antd draws table headers at 600 and ng-zorro at 500. Column headers are the
// one difference visible side by side on this page, so match the shell's weight.
const TABLE_TOKENS = { fontWeightStrong: 500 };

export const ConfigurationTable = ({ entries = [] }: ConfigurationTableProps) => {
  const rows: ConfigurationRow[] = entries.map(([name, value]) => ({ key: name, name, value }));

  return (
    // Deliberately the same id the Angular table carries, so page-level specs
    // work on either side of the flag.
    <div data-testid="configuration-table">
      <Table<ConfigurationRow> columns={COLUMNS} dataSource={rows} size="small" pagination={false} />
    </div>
  );
};

export interface ConfigurationTableMountHandle {
  update: (props: ConfigurationTableProps) => void;
  unmount: () => void;
}

export const mount = (element: HTMLElement, initialProps: ConfigurationTableProps): ConfigurationTableMountHandle => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root: Root = createRoot(element);

  const renderWith = (props: ConfigurationTableProps) => {
    root.render(
      <ReactErrorBoundary onError={props.onError}>
        <ZeppelinThemeProvider token={TABLE_TOKENS}>
          <ConfigurationTable {...props} />
        </ZeppelinThemeProvider>
      </ReactErrorBoundary>
    );
  };

  renderWith(initialProps);

  return {
    update: (newProps: ConfigurationTableProps) => {
      renderWith(newProps);
    },
    unmount: () => {
      root.unmount();
    }
  };
};
