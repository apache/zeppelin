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

export interface NotebookRepoListSnapshot {
  repositories: NotebookRepo[];
  /** Host-assigned sequence of the GET that produced this list. */
  version: number;
}

export interface NotebookRepoListProps {
  snapshot?: NotebookRepoListSnapshot;
  /** Resolves with server state fetched after this save request, including after a failed PUT. */
  onSave?: (repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>;
  onError?: (error: unknown) => void;
}

// ng-zorro draws card titles and table headers at 500 where antd uses 600.
const REPO_TOKENS = { fontWeightStrong: 500 };

// The Angular card spaces itself with @card-padding-base from the default theme.
const CARD_GAP = 24;
const EMPTY_SNAPSHOT: NotebookRepoListSnapshot = { repositories: [], version: 0 };

// Stricter than the Angular form's Validators.required, which accepts whitespace-only input.
const isBlank = (value: string): boolean => value.trim().length === 0;

interface RepoCardProps {
  repo: NotebookRepo;
  onSave: (repo: NotebookRepo) => Promise<void> | undefined;
}

// `setting.name` is the join key everywhere below (draft, save, cancel, render), never array
// position, since `repo.settings` order isn't guaranteed stable between refetches and a
// same-length reorder mid-edit would otherwise submit a value under the wrong name.
const draftFromSettings = (settings: NotebookRepoSetting[]): Record<string, string> =>
  Object.fromEntries(settings.map(setting => [setting.name, setting.selected]));

const RepoCard = ({ repo, onSave }: RepoCardProps) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Record<string, string>>(() => draftFromSettings(repo.settings));
  const [pendingSave, setPendingSave] = useState<{
    request: Promise<void>;
    draft: Record<string, string>;
  } | null>(null);

  useEffect(() => {
    const namesUnchanged =
      repo.settings.length === Object.keys(draft).length &&
      repo.settings.every(setting => Object.hasOwn(draft, setting.name));
    if (namesUnchanged && (editing || pendingSave)) {
      return;
    }
    setPendingSave(null);
    setDraft(draftFromSettings(repo.settings));
    // Resync on server updates or save completion, not on keystrokes or toggling edit mode.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repo, pendingSave]);

  const invalid = Object.values(draft).some(isBlank);

  const save = () => {
    if (invalid) {
      return;
    }
    const values = { ...draft };
    const request = onSave({
      ...repo,
      settings: repo.settings.map(setting => ({ ...setting, selected: values[setting.name] }))
    });
    // The mount bridge can return undefined when the host callback throws.
    if (!request) {
      return;
    }
    setPendingSave({ request, draft: values });
    setEditing(false);
    const finish = () => {
      setPendingSave(current => (current?.request === request ? null : current));
    };
    void request.then(finish, finish);
  };

  const cancel = () => {
    setDraft(pendingSave ? pendingSave.draft : draftFromSettings(repo.settings));
    setEditing(false);
  };

  const setValue = (name: string, value: string) => setDraft(current => ({ ...current, [name]: value }));

  const columns = [
    { title: 'Name', dataIndex: 'name', key: 'name', width: '30%' },
    {
      title: 'Value',
      key: 'value',
      render: (_: unknown, setting: NotebookRepoSetting) => {
        if (!editing) {
          // While waiting on the host's refetch, show what was just saved rather than the pre-edit value still sitting in `repo`.
          return pendingSave ? pendingSave.draft[setting.name] : setting.selected;
        }
        if (setting.type === 'DROPDOWN') {
          return (
            <Select
              size="small"
              value={draft[setting.name]}
              onChange={value => setValue(setting.name, value)}
              options={setting.value.map(option => ({ label: option, value: option }))}
              style={{ minWidth: 160 }}
            />
          );
        }
        return (
          <Input
            size="small"
            value={draft[setting.name]}
            onChange={event => setValue(setting.name, event.target.value)}
          />
        );
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

export const NotebookRepoList = ({ snapshot = EMPTY_SNAPSHOT, onSave }: NotebookRepoListProps) => {
  const [latestSnapshot, setLatestSnapshot] = useState(snapshot);
  const currentSnapshot = snapshot.version > latestSnapshot.version ? snapshot : latestSnapshot;
  useEffect(() => {
    setLatestSnapshot(current => (snapshot.version > current.version ? snapshot : current));
  }, [snapshot]);

  const saveRepo = (repo: NotebookRepo): Promise<void> | undefined =>
    onSave?.(repo)?.then(result => {
      // The result may arrive before Angular forwards the new props. Publish it before the card leaves pending.
      setLatestSnapshot(current => (result.version > current.version ? result : current));
    });

  return (
    <div data-testid="notebook-repo-list">
      {currentSnapshot.repositories.map((repo, index) => (
        // Include the index because zeppelin.notebook.storage can contain duplicate classes.
        // Reordering repositories can still remount cards; the server supplies no stable repository ID.
        <RepoCard key={`${repo.className}-${index}`} repo={repo} onSave={saveRepo} />
      ))}
    </div>
  );
};

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
