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

import { useEffect, useRef, useState } from 'react';
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

// Stricter than the Angular form's Validators.required, which accepts whitespace-only input.
const isBlank = (value: string): boolean => value.trim().length === 0;

interface RepoCardProps {
  repo: NotebookRepo;
  onRepoChange?: (repo: NotebookRepo) => void;
}

// `setting.name` is the join key everywhere below (draft, save, cancel, render), never array
// position, since `repo.settings` order isn't guaranteed stable between refetches and a
// same-length reorder mid-edit would otherwise submit a value under the wrong name.
const draftFromSettings = (settings: NotebookRepoSetting[]): Record<string, string> =>
  Object.fromEntries(settings.map(setting => [setting.name, setting.selected]));

const RepoCard = ({ repo, onRepoChange }: RepoCardProps) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Record<string, string>>(() => draftFromSettings(repo.settings));
  // Set on save, cleared by the next `repo` prop change (the host's refetch, success or failure,
  // is the source of truth for what got persisted). View mode reads the draft while this is true,
  // so the card shows the saved value instead of the pre-edit one during the round trip. Not
  // gated on the refetch matching what was submitted, since several NotebookRepo implementations
  // legitimately refetch something else (plugin repos' updateSettings is a no-op, VFS/Git
  // normalize the path), and that has to win immediately.
  const [pendingSave, setPendingSave] = useState(false);
  const editingRef = useRef(editing);
  editingRef.current = editing;
  // The draft submitted by the most recent unconfirmed save, paired with the `repo` it was made
  // against. Lets cancel() tell apart "no refetch yet" (repo unchanged, restore the saved draft)
  // from "a refetch already landed mid-edit" (repo changed, that refetch wins).
  const savedRef = useRef<{ repo: NotebookRepo; draft: Record<string, string> } | null>(null);

  // Rebuilds the draft from a refetched repo. Skipped while editing, so an unrelated refetch
  // (e.g. another card's save) can't discard in-progress keystrokes. Deps only on `repo`, so
  // toggling `editing` doesn't re-run this against a repo prop the host hasn't updated yet. Still
  // resyncs if the setting names changed underneath the edit, since view mode and save() look
  // values up by name, so a missing name would render/submit `undefined`.
  //
  // KNOWN LIMITATION (ZEPPELIN-6707): this only guards an in-progress *edit*, not an in-progress
  // *pendingSave* on a different card. If card B calls save() and, before B's own refetch lands,
  // card A's independent save triggers the host's list-wide getRepos(), B isn't editing so this
  // effect fires, resets B's pendingSave to B's still-stale server value, and B's display
  // flickers backward before jumping forward again once B's own refetch arrives. A real fix needs
  // the host to tell B's refetch apart from A's, since value or reference comparison alone can't
  // (see the ticket).
  useEffect(() => {
    // Object.hasOwn, not `in`, since `in` walks the prototype chain, so a setting literally named
    // e.g. "toString" would read as already present.
    const namesUnchanged =
      repo.settings.length === Object.keys(draft).length &&
      repo.settings.every(setting => Object.hasOwn(draft, setting.name));
    if (editingRef.current && namesUnchanged) {
      return;
    }
    savedRef.current = null;
    setDraft(draftFromSettings(repo.settings));
    setPendingSave(false);
    // Deliberately `[repo]` only, not `draft`, since the effect must run exactly when `repo`
    // changes, not when `draft` does (typing would retrigger it). `draft` inside the guard is
    // read from this render's closure regardless of what's in the deps array, so it's always
    // current when the effect actually runs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repo]);

  const invalid = Object.values(draft).some(isBlank);

  const save = () => {
    if (invalid) {
      return;
    }
    const values = { ...draft };
    onRepoChange?.({
      ...repo,
      settings: repo.settings.map(setting => ({ ...setting, selected: values[setting.name] }))
    });
    savedRef.current = { repo, draft: values };
    setPendingSave(true);
    setEditing(false);
  };

  const cancel = () => {
    // Pending and still against the same repo: restore the saved draft, not the stale pre-edit
    // `repo` value or a further in-progress edit. Otherwise a refetch already moved `repo` on, so
    // defer to it instead.
    const saved = savedRef.current;
    if (pendingSave && saved && saved.repo === repo) {
      setDraft(saved.draft);
    } else {
      savedRef.current = null;
      setPendingSave(false);
      setDraft(draftFromSettings(repo.settings));
    }
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
          return pendingSave ? draft[setting.name] : setting.selected;
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

export const NotebookRepoList = ({ repositories = [], onRepoChange }: NotebookRepoListProps) => (
  <div data-testid="notebook-repo-list">
    {repositories.map((repo, index) => (
      // Suffixed with the index, since zeppelin.notebook.storage isn't deduped server-side, so
      // className alone would collide if listed twice. Trade-off: repo objects carry no stable
      // id, so a same-length reorder (the host sorts by `name.charCodeAt(0)` only, which doesn't
      // fully order same-first-letter names) changes this key too, remounting the card and losing
      // any in-progress edit - the index can't tell "this repo moved" from "a different repo is
      // now here".
      <RepoCard key={`${repo.className}-${index}`} repo={repo} onRepoChange={onRepoChange} />
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
