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
import { mount, NotebookRepo, NotebookRepoListMountHandle, NotebookRepoListProps } from './NotebookRepoList';

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

describe('NotebookRepoList mount contract', () => {
  let host: HTMLElement | null = null;
  let handle: NotebookRepoListMountHandle | null = null;

  const mountList = (props: NotebookRepoListProps): void => {
    host = document.createElement('div');
    document.body.appendChild(host);
    act(() => {
      handle = mount(host as HTMLElement, props);
    });
  };

  const card = (repoName: string): HTMLElement => within(host!).getByText(repoName).closest('.ant-card') as HTMLElement;

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
    mountList({ repositories: [gitRepo(), dropdownRepo()] });

    expect(typeof handle!.update).toBe('function');
    expect(typeof handle!.unmount).toBe('function');
    expect(within(host!).getByText('GitNotebookRepo')).toBeTruthy();
    expect(within(host!).getByText('S3NotebookRepo')).toBeTruthy();
  });

  it('shows each setting as name and value until the card is edited', () => {
    mountList({ repositories: [gitRepo()] });

    expect(within(host!).getByText('Notebook Path')).toBeTruthy();
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByRole('textbox')).toBeNull();
  });

  it('renders no cards when the host has no repositories yet', () => {
    mountList({});

    expect(host!.querySelector('[data-testid="notebook-repo-list"]')).not.toBeNull();
    expect(host!.querySelectorAll('.ant-card')).toHaveLength(0);
  });

  it('offers an input for INPUT settings and a dropdown for DROPDOWN settings', () => {
    mountList({ repositories: [gitRepo(), dropdownRepo()] });

    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toBeTruthy();

    clickButton('S3NotebookRepo', /Edit/);
    expect(within(card('S3NotebookRepo')).getByRole('combobox')).toBeTruthy();
  });

  it('reports the edited settings to the host on save', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);

    // The host owns the PUT, so it gets the whole repo, not a partial patch.
    expect(onRepoChange).toHaveBeenCalledTimes(1);
    expect(onRepoChange.mock.calls[0][0]).toEqual({
      ...gitRepo(),
      settings: [{ ...gitRepo().settings[0], selected: '/srv/notebook' }]
    });
  });

  it('reports the selected option to the host when a DROPDOWN setting is saved', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [dropdownRepo()], onRepoChange });

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

    expect(onRepoChange).toHaveBeenCalledTimes(1);
    expect(onRepoChange.mock.calls[0][0]).toEqual({
      ...dropdownRepo(),
      settings: [{ ...dropdownRepo().settings[0], selected: 'eu-west-1' }]
    });
  });

  it('leaves the host alone and restores the value on cancel', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'discard me' } });
    });
    clickButton('GitNotebookRepo', /Cancel/);

    expect(onRepoChange).not.toHaveBeenCalled();
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/opt/zeppelin/notebook');
  });

  it('keeps showing the saved value if the card is cancelled before the host refetch lands', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

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
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '   ' } });
    });

    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toHaveProperty('disabled', true);
    expect(onRepoChange).not.toHaveBeenCalled();
  });

  it('shows the saved value immediately, before the host refetch lands', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);

    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/opt/zeppelin/notebook')).toBeNull();
  });

  it('keeps in-progress edits when an unrelated refetch updates the list', () => {
    mountList({ repositories: [gitRepo(), dropdownRepo()] });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'still typing' } });
    });

    const h = handle!;
    act(() => h.update({ repositories: [gitRepo(), dropdownRepo()] }));

    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', 'still typing');
    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toBeTruthy();
  });

  it('resyncs the draft if the setting count changes mid-edit, instead of indexing past it', () => {
    mountList({ repositories: [gitRepo()] });

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
    act(() => h.update({ repositories: [grown] }));

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
    mountList({ repositories: [twoSettings] });

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
    act(() => h.update({ repositories: [gitRepo()] }));

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
    const onRepoChange = vi.fn();
    mountList({ repositories: [original], onRepoChange });

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
    act(() => h.update({ repositories: [reordered], onRepoChange }));

    clickButton('GitNotebookRepo', /Save/);

    expect(onRepoChange).toHaveBeenCalledTimes(1);
    const submitted = onRepoChange.mock.calls[0][0] as NotebookRepo;
    const byName = Object.fromEntries(submitted.settings.map(setting => [setting.name, setting.selected]));
    expect(byName['Notebook Path']).toBe('/srv/notebook');
    expect(byName['One Way Sync']).toBe('true');
  });

  // KNOWN LIMITATION (ZEPPELIN-6707): unlike the editing case above, a card with pendingSave
  // still true is not protected from an unrelated card's refetch, because the resync effect only
  // checks `editingRef`.
  // This test pins that current (self-correcting, not data-losing) behavior rather than asserting
  // the fix - see the ticket for why a value/reference check alone can't distinguish "my own
  // confirming refetch" from "someone else's".
  // Its first half exercises the same guard as "accepts a refetch even when it differs from what
  // was submitted" below (a single-card refetch reads the same at the component level as an
  // unrelated card's list-wide one); the second-card list and the resync-after-flicker assertion
  // at the end are what this test actually adds.
  it('flickers back to the stale server value if an unrelated refetch lands before its own pendingSave confirms', () => {
    mountList({ repositories: [gitRepo(), dropdownRepo()] });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);
    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();

    // Another card's save resolves first and triggers the host's list-wide refetch; this card's
    // own PUT hasn't landed yet, so the refetch still carries its pre-edit value.
    const h = handle!;
    act(() => h.update({ repositories: [gitRepo(), dropdownRepo()] }));
    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/srv/notebook')).toBeNull();

    // This card's own refetch arrives moments later and self-corrects.
    act(() =>
      h.update({
        repositories: [
          { ...gitRepo(), settings: [{ ...gitRepo().settings[0], selected: '/srv/notebook' }] },
          dropdownRepo()
        ]
      })
    );
    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
  });

  it('accepts a refetch even when it differs from what was submitted', () => {
    // Plugin repos' updateSettings is a no-op and VFS/Git normalize the path,
    // so the host can legitimately refetch something else; that has to win.
    mountList({ repositories: [gitRepo()], onRepoChange: vi.fn() });
    const h = handle!;

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);
    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();

    act(() => h.update({ repositories: [gitRepo()] }));

    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/srv/notebook')).toBeNull();
  });

  it('restores the saved value, not a further in-progress edit, on cancel before the refetch lands', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);
    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: 'garbage' } });
    });
    clickButton('GitNotebookRepo', /Cancel/);

    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
    expect(within(host!).queryByText('garbage')).toBeNull();
    expect(onRepoChange).toHaveBeenCalledTimes(1);
  });

  it('resyncs to a refetch that landed mid-edit once the edit is cancelled', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });
    const h = handle!;

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '/srv/notebook' } });
    });
    clickButton('GitNotebookRepo', /Save/);

    // The [repo] effect skips this refetch since the card is already back in edit mode by the
    // time it lands.
    clickButton('GitNotebookRepo', /Edit/);
    act(() =>
      h.update({
        repositories: [{ ...gitRepo(), settings: [{ ...gitRepo().settings[0], selected: '/opt/zeppelin/notebook' }] }]
      })
    );
    clickButton('GitNotebookRepo', /Cancel/);

    expect(within(host!).getByText('/opt/zeppelin/notebook')).toBeTruthy();
    expect(within(host!).queryByText('/srv/notebook')).toBeNull();

    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/opt/zeppelin/notebook');
  });

  it('shows the refetched values after the host updates the repositories', () => {
    mountList({ repositories: [gitRepo()] });

    const saved: NotebookRepo = {
      ...gitRepo(),
      settings: [{ ...gitRepo().settings[0], selected: '/srv/notebook' }]
    };
    const h = handle!;
    act(() => h.update({ repositories: [saved] }));

    expect(within(host!).getByText('/srv/notebook')).toBeTruthy();
    clickButton('GitNotebookRepo', /Edit/);
    expect(within(card('GitNotebookRepo')).getByRole('textbox')).toHaveProperty('value', '/srv/notebook');
  });

  it('unmount() empties the host element', () => {
    mountList({ repositories: [gitRepo()] });
    const h = handle!;
    handle = null;

    act(() => h.unmount());

    expect(host!.innerHTML).toBe('');
  });
});
