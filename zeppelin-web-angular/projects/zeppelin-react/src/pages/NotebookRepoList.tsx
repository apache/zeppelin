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

import { useEffect, useState } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { CloseOutlined, EditOutlined, SaveOutlined } from '@ant-design/icons';
import { Button, Card, Input, Select, Space, Table } from 'antd';
import { ReactErrorBoundary } from '@/components';
import { ZeppelinThemeProvider } from '@/theme';

/** Mirrors the shell's NotebookRepoSettingsItem; the SDK does not declare it. */
export interface NotebookRepoSetting {
  type: string;
  value: string[];
  selected: string;
  name: string;
}

export interface NotebookRepo {
  name: string;
  className: string;
  settings: NotebookRepoSetting[];
}

export interface NotebookRepoListProps {
  repositories?: NotebookRepo[];
  /** The host owns the PUT and the refetch; this only reports the edited repo. */
  onRepoChange?: (repo: NotebookRepo) => void;
  onError?: (error: unknown) => void;
}

// ng-zorro draws card titles and table headers at 500 where antd uses 600.
const REPO_TOKENS = { fontWeightStrong: 500 };

// The Angular card spaces itself with @card-padding-base from the default theme.
const CARD_GAP = 24;

const isBlank = (value: string): boolean => value.trim().length === 0;

interface RepoCardProps {
  repo: NotebookRepo;
  onRepoChange?: (repo: NotebookRepo) => void;
}

const RepoCard = ({ repo, onRepoChange }: RepoCardProps) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<string[]>(() => repo.settings.map(setting => setting.selected));

  // A save reaches the host, which refetches and hands the repo back down.
  // Rebuilding the draft on that keeps the form from showing stale values.
  useEffect(() => {
    setDraft(repo.settings.map(setting => setting.selected));
  }, [repo]);

  const invalid = draft.some(isBlank);

  const save = () => {
    if (invalid) {
      return;
    }
    onRepoChange?.({
      ...repo,
      settings: repo.settings.map((setting, index) => ({ ...setting, selected: draft[index] }))
    });
    setEditing(false);
  };

  const cancel = () => {
    setDraft(repo.settings.map(setting => setting.selected));
    setEditing(false);
  };

  const setValue = (index: number, value: string) =>
    setDraft(current => current.map((entry, i) => (i === index ? value : entry)));

  const columns = [
    { title: 'Name', dataIndex: 'name', key: 'name', width: '30%' },
    {
      title: 'Value',
      key: 'value',
      render: (_: unknown, setting: NotebookRepoSetting, index: number) => {
        if (!editing) {
          return setting.selected;
        }
        if (setting.type === 'DROPDOWN') {
          return (
            <Select
              size="small"
              value={draft[index]}
              onChange={value => setValue(index, value)}
              options={setting.value.map(option => ({ label: option, value: option }))}
              style={{ minWidth: 160 }}
            />
          );
        }
        return <Input size="small" value={draft[index]} onChange={event => setValue(index, event.target.value)} />;
      }
    }
  ];

  // Icons mirror the Angular card's nz-icon edit/save/close.
  const extra = editing ? (
    <Space size={8}>
      <Button type="primary" size="small" icon={<SaveOutlined />} disabled={invalid} onClick={save}>
        Save
      </Button>
      <Button size="small" icon={<CloseOutlined />} onClick={cancel}>
        Cancel
      </Button>
    </Space>
  ) : (
    <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(true)}>
      Edit
    </Button>
  );

  return (
    <Card
      title={repo.name}
      extra={extra}
      size="small"
      style={{ marginBottom: CARD_GAP }}
      data-testid="notebook-repo-item"
      data-repo-name={repo.name}
    >
      <h3>Setting</h3>
      <Table<NotebookRepoSetting>
        columns={columns}
        dataSource={repo.settings.map((setting, index) => ({ ...setting, key: `${setting.name}-${index}` }))}
        size="small"
        pagination={false}
      />
    </Card>
  );
};

export const NotebookRepoList = ({ repositories = [], onRepoChange }: NotebookRepoListProps) => (
  <div data-testid="notebook-repo-list">
    {repositories.map(repo => (
      <RepoCard key={repo.className} repo={repo} onRepoChange={onRepoChange} />
    ))}
  </div>
);

export interface NotebookRepoListMountHandle {
  update: (props: NotebookRepoListProps) => void;
  unmount: () => void;
}

export const mount = (element: HTMLElement, initialProps: NotebookRepoListProps): NotebookRepoListMountHandle => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root: Root = createRoot(element);

  const renderWith = (props: NotebookRepoListProps) => {
    root.render(
      <ReactErrorBoundary onError={props.onError}>
        <ZeppelinThemeProvider token={REPO_TOKENS}>
          <NotebookRepoList {...props} />
        </ZeppelinThemeProvider>
      </ReactErrorBoundary>
    );
  };

  renderWith(initialProps);

  return {
    update: (newProps: NotebookRepoListProps) => renderWith(newProps),
    unmount: () => root.unmount()
  };
};
