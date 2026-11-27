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
import { fireEvent, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  mount,
  NotebookRepo,
  NotebookRepoListMountHandle,
  NotebookRepoListProps,
  NotebookRepoListSnapshot
} from './NotebookRepoList';

const gitRepo = (): NotebookRepo => ({
  name: 'GitNotebookRepo',
  className: 'org.apache.zeppelin.notebook.repo.GitNotebookRepo',
  settings: [{ type: 'INPUT', value: [], selected: '/opt/zeppelin/notebook', name: 'Notebook Path' }]
});

const dropdownRepo = (): NotebookRepo => ({
  name: 'S3NotebookRepo',
  className: 'org.apache.zeppelin.notebook.repo.S3NotebookRepo',
  settings: [{ type: 'DROPDOWN', value: ['us-east-1', 'eu-west-1'], selected: 'us-east-1', name: 'Region' }]
});

const serverSnapshot = (repositories: NotebookRepo[], version = 1): NotebookRepoListSnapshot => ({
  repositories,
  version
});
const pendingResult = (): Promise<NotebookRepoListSnapshot> => new Promise(() => undefined);
const deferredSave = () => {
  let resolve!: (snapshot: NotebookRepoListSnapshot) => void;
  let reject!: (error: Error) => void;
  const result = new Promise<NotebookRepoListSnapshot>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { result, resolve, reject };
};

describe('NotebookRepoList mount contract', () => {
  let host: HTMLElement | null = null;
  let handle: NotebookRepoListMountHandle | null = null;

  const mountList = (props: NotebookRepoListProps): void => {
    host = document.createElement('div');
    document.body.appendChild(host);
    act(() => {
      handle = mount(host as HTMLElement, { onSave: pendingResult, ...props });
    });
  };

  const card = (repoName: string): HTMLElement =>
    within(host!).getByText(repoName).closest('.zeppelin-react-card') as HTMLElement;

  const clickButton = (repoName: string, name: RegExp): void => {
    act(() => {
      fireEvent.click(within(card(repoName)).getByRole('button', { name }));
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
    expect(() => mount(null as unknown as HTMLElement, {})).toThrow('Mount element is required');
  });

  it('returns an update/unmount handle and renders one card per repository', () => {
    mountList({ snapshot: serverSnapshot([gitRepo(), dropdownRepo()], 1) });

    expect(typeof handle!.update).toBe('function');
    expect(typeof handle!.unmount).toBe('function');
    expect(within(host!).getByText('GitNotebookRepo')).toBeTruthy();
    expect(within(host!).getByText('S3NotebookRepo')).toBeTruthy();
  });

  it('isolates React table classes from the Angular shell styles', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()], 1) });

    expect(host!.querySelector('.zeppelin-react-table')).not.toBeNull();
    expect(host!.querySelector('[class^="ant-"], [class*=" ant-"]')).toBeNull();
  });

  it('shows each setting as name and value until the card is edited', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()], 1) });

    expect(within(host!).getByText('Notebook Path')).toBeTruthy();
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByRole('textbox')).toBeNull();
  });

  it('renders no cards when the host has no repositories yet', () => {
    mountList({});

    expect(host!.querySelector('[data-testid="notebook-repo-list"]')).not.toBeNull();
    expect(host!.querySelectorAll('.zeppelin-react-card')).toHaveLength(0);
  });

  it('offers an input for INPUT settings and a dropdown for DROPDOWN settings', () => {
    mountList({ snapshot: serverSnapshot([gitRepo(), dropdownRepo()], 1) });

    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toBeTruthy();

    clickButton('S3NotebookRepo', /Edit/);
    expect(within(card('S3NotebookRepo')).getByRole('combobox')).toBeTruthy();
  });

  it('reports the edited settings to the host on save', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([gitRepo()], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);

    // The host owns the PUT, so it gets the whole repo, not a partial patch.
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave.mock.calls[0][0]).toEqual({
      ...gitRepo(),
      settings: [{ ...gitRepo().settings[0], selected: '/srv/notebook' }]
    });
  });

  it('reports the selected option to the host when a DROPDOWN setting is saved', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([dropdownRepo()], 1), onSave });

    clickButton('S3NotebookRepo', /Edit/);
    const combobox = within(card('S3NotebookRepo')).getByRole('combobox');
    act(() => {
      fireEvent.mouseDown(combobox);
    });
    // antd renders the option list in a portal, not inside the card.
    act(() => {
      fireEvent.click(within(document.body).getByTitle('eu-west-1'));
    });
    clickButton('S3NotebookRepo', /Save/);

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave.mock.calls[0][0]).toEqual({
      ...dropdownRepo(),
      settings: [{ ...dropdownRepo().settings[0], selected: 'eu-west-1' }]
    });
  });

  it('leaves the host alone and restores the value on cancel', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([gitRepo()], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'discard me' } });
    });
    clickButton('GitNotebookRepo', /Cancel/);

    expect(onSave).not.toHaveBeenCalled();
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/opt/zeppelin/notebook');
  });

  it('keeps showing the saved value if the card is cancelled before the host refetch lands', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([gitRepo()], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);
    clickButton('GitNotebookRepo', /Edit/);
    clickButton('GitNotebookRepo', /Cancel/);

    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/opt/zeppelin/notebook')).toBeNull();
  });

  it('refuses to save a blank setting', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([gitRepo()], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '   ' } });
    });

    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toHaveProperty('disabled', true);
    expect(onSave).not.toHaveBeenCalled();
  });

  it('shows the saved value immediately, before the host refetch lands', () => {
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([gitRepo()], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);

    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/opt/zeppelin/notebook')).toBeNull();
  });

  it('keeps in-progress edits when an unrelated refetch updates the list', () => {
    mountList({ snapshot: serverSnapshot([gitRepo(), dropdownRepo()], 1) });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'still typing' } });
    });

    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([gitRepo(), dropdownRepo()], 2) }));

    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', 'still typing');
    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toBeTruthy();
  });

  it('resyncs the draft if the setting count changes mid-edit, instead of indexing past it', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()], 1) });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'still typing' } });
    });

    // The backing NotebookRepo now reports one more setting than it did when editing started -
    // e.g. its settings schema isn't stable. `draft` was sized for the old, shorter
    // `repo.settings`.
    const grown = {
      ...gitRepo(),
      settings: [...gitRepo().settings, { type: 'INPUT', value: [], selected: '/srv/second', name: 'Second Path' }]
    };
    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([grown], 2) }));

    // Resynced to the host's latest values for both rows - not the stale in-progress edit, and
    // not `draft[1]` read as undefined for the row that didn't exist when `draft` was last sized.
    const textboxes = within(card('GitNotebookRepo')).getAllByRole('textbox');
    expect(textboxes).toHaveLength(2);
    expect(textboxes[0]).toHaveProperty('value', '/opt/zeppelin/notebook');
    expect(textboxes[1]).toHaveProperty('value', '/srv/second');
  });

  it('resyncs the draft if the setting count shrinks mid-edit, instead of leaving a stale blank key stuck', () => {
    const twoSettings: NotebookRepo = {
      ...gitRepo(),
      settings: [...gitRepo().settings, { type: 'INPUT', value: [], selected: '/srv/second', name: 'Second Path' }]
    };
    mountList({ snapshot: serverSnapshot([twoSettings], 1) });

    clickButton('GitNotebookRepo', /Edit/);
    const textboxesBefore = within(card('GitNotebookRepo')).getAllByRole('textbox');
    act(() => {
      // Blanking this row correctly disables Save. It's about to disappear from `repo.settings`
      // below - its now-stale key would keep `invalid` true forever if the draft is never rebuilt
      // to drop it.
      fireEvent.change(textboxesBefore[1], { target: { value: '' } });
    });
    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toHaveProperty('disabled', true);

    // The backing NotebookRepo drops the now-blank setting entirely - the length half of
    // `namesUnchanged` is what catches this; the `every(...)` half alone would still read as
    // "unchanged", since every remaining name is still present in `draft`.
    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([gitRepo()], 2), onSave: pendingResult }));

    // Resynced: the stale blank key is gone, so Save is enabled again for the one remaining,
    // non-blank row.
    expect(within(card('GitNotebookRepo')).getAllByRole('textbox')).toHaveLength(1);
    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toHaveProperty('disabled', false);
  });

  it('matches saved values to the right setting by name, even if repo.settings is reordered mid-edit', () => {
    const original: NotebookRepo = {
      name: 'GitNotebookRepo',
      className: 'org.apache.zeppelin.notebook.repo.GitNotebookRepo',
      settings: [
        { type: 'INPUT', value: [], selected: '/opt/zeppelin/notebook', name: 'Notebook Path' },
        { type: 'INPUT', value: [], selected: 'true', name: 'One Way Sync' }
      ]
    };
    const onSave = vi.fn<(repo: NotebookRepo) => Promise<NotebookRepoListSnapshot>>(pendingResult);
    mountList({ snapshot: serverSnapshot([original], 1), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    const textboxesBefore = within(card('GitNotebookRepo')).getAllByRole('textbox');
    act(() => {
      fireEvent.change(textboxesBefore[0], { target: { value: '/srv/notebook' } });
    });

    // Same setting names, different order - e.g. a refetch whose response order isn't guaranteed
    // to match the last one. Same length and names, so the guard keeps this card in edit mode
    // rather than discarding the in-progress edit above.
    const reordered: NotebookRepo = { ...original, settings: [original.settings[1], original.settings[0]] };
    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([reordered], 2), onSave }));

    clickButton('GitNotebookRepo', /Save/);

    expect(onSave).toHaveBeenCalledTimes(1);
    const submitted = onSave.mock.calls[0][0] as NotebookRepo;
    const byName = Object.fromEntries(submitted.settings.map(setting => [setting.name, setting.selected]));
    expect(byName['Notebook Path']).toBe('/srv/notebook');
    expect(byName['One Way Sync']).toBe('true');
  });

  it('keeps a pending save through another card completing its save', async () => {
    const saveB = deferredSave();
    const saveA = deferredSave();
    const onSave = vi.fn().mockReturnValueOnce(saveB.result).mockReturnValueOnce(saveA.result);
    mountList({ snapshot: serverSnapshot([gitRepo(), dropdownRepo()]), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);
    clickButton('S3NotebookRepo', /Edit/);
    clickButton('S3NotebookRepo', /Save/);

    await act(async () => saveA.resolve(serverSnapshot([gitRepo(), dropdownRepo()], 2)));
    expect(within(card('GitNotebookRepo')).getByText('/submitted')).toBeTruthy();
    expect(within(card('GitNotebookRepo')).queryByText('/opt/zeppelin/notebook')).toBeNull();

    const saved = { ...gitRepo(), settings: [{ ...gitRepo().settings[0], selected: '/server-value' }] };
    await act(async () => saveB.resolve(serverSnapshot([saved, dropdownRepo()], 3)));
    expect(within(card('GitNotebookRepo')).getByText('/server-value')).toBeTruthy();
    expect(within(card('GitNotebookRepo')).queryByText('/submitted')).toBeNull();
  });

  it('uses a different server value before Angular forwards the snapshot', async () => {
    const save = deferredSave();
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave: () => save.result });
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);

    await act(async () => save.resolve(serverSnapshot([gitRepo()], 2)));
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/submitted')).toBeNull();

    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([{ ...gitRepo(), settings: [] }], 1) }));
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
  });

  it('keeps newer props when an older save result arrives later', async () => {
    const save = deferredSave();
    const onSave = () => save.result;
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave });
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);

    const latest = { ...gitRepo(), settings: [{ ...gitRepo().settings[0], selected: '/latest' }] };
    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([latest], 3), onSave }));
    expect(within(host!).getByText('/submitted')).toBeTruthy();
    await act(async () => save.resolve(serverSnapshot([gitRepo()], 2)));
    expect(within(host!).getByText('/latest')).toBeTruthy();

    act(() => h.update({ snapshot: serverSnapshot([gitRepo()], 1), onSave }));
    expect(within(host!).getByText('/latest')).toBeTruthy();
  });

  it('does not let an older save completion clear a newer pending save', async () => {
    const first = deferredSave();
    const second = deferredSave();
    const onSave = vi.fn().mockReturnValueOnce(first.result).mockReturnValueOnce(second.result);
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/first' } }));
    clickButton('GitNotebookRepo', /Save/);
    clickButton('GitNotebookRepo', /Edit/);
    act(() => fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/second' } }));
    clickButton('GitNotebookRepo', /Save/);

    await act(async () => first.resolve(serverSnapshot([gitRepo()], 2)));
    expect(within(host!).getByText('/second')).toBeTruthy();
    expect(within(host!).queryByText('/opt/zeppelin/notebook')).toBeNull();

    await act(async () => second.resolve(serverSnapshot([gitRepo()], 3)));
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
  });

  it('restores the submitted value on cancel after an unrelated refetch', () => {
    const onSave = pendingResult;
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave });
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);

    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([gitRepo()], 2), onSave }));
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/discard' } })
    );
    clickButton('GitNotebookRepo', /Cancel/);
    expect(within(host!).getByText('/submitted')).toBeTruthy();
    expect(within(host!).queryByText('/discard')).toBeNull();
  });

  it('keeps typing on save completion and restores the server value on cancel', async () => {
    const save = deferredSave();
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave: () => save.result });
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/still-typing' } })
    );

    await act(async () => save.resolve(serverSnapshot([gitRepo()], 2)));
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/still-typing');
    clickButton('GitNotebookRepo', /Cancel/);
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
  });

  it('restores known server state when the save callback rejects', async () => {
    const save = deferredSave();
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave: () => save.result });
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/submitted' } })
    );
    clickButton('GitNotebookRepo', /Save/);
    await act(async () => save.reject(new Error('host unavailable')));

    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/submitted')).toBeNull();
  });

  it('keeps editing if the host callback is absent or swallowed by the mount bridge', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()]), onSave: undefined });
    clickButton('GitNotebookRepo', /Edit/);
    clickButton('GitNotebookRepo', /Save/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toBeTruthy();
  });

  it('shows refetched values when there is no pending save', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()]) });
    const saved = { ...gitRepo(), settings: [{ ...gitRepo().settings[0], selected: '/server-value' }] };
    const h = handle!;
    act(() => h.update({ snapshot: serverSnapshot([saved], 2) }));
    expect(within(host!).getByText('/server-value')).toBeTruthy();
    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/server-value');
  });

  it('unmount() empties the host element', () => {
    mountList({ snapshot: serverSnapshot([gitRepo()]) });
    const h = handle!;
    handle = null;
    act(() => h.unmount());
    expect(host!.innerHTML).toBe('');
  });
});
