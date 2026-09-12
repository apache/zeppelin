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

    // The host owns the PUT, so what it receives is the whole repo with the
    // edited value in place, not a partial patch.
    expect(onRepoChange).toHaveBeenCalledTimes(1);
    expect(onRepoChange.mock.calls[0][0]).toEqual({
      ...gitRepo(),
      settings: [{ ...gitRepo().settings[0], selected: '/srv/notebook' }]
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

  it('refuses to save a blank setting', () => {
    const onRepoChange = vi.fn();
    mountList({ repositories: [gitRepo()], onRepoChange });

    clickButton('GitNotebookRepo', /Edit/);
    act(() => {
      fireEvent.change(within(card('GitNotebookRepo')).getByRole('textbox'), { target: { value: '   ' } });
    });

    // Matches the Angular form's required validator rather than sending a PUT
    // the server would reject.
    expect(within(card('GitNotebookRepo')).getByRole('button', { name: /Save/ })).toHaveProperty('disabled', true);
    expect(onRepoChange).not.toHaveBeenCalled();
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
