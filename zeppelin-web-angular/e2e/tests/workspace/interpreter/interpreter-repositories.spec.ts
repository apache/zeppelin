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

import { expect, openInterpreterPage, stubInterpreterRead, test } from '../../../models/interpreter-test.util';
import { InterpreterRepositoryModal } from '../../../models/interpreter-repository-modal';
import { InterpreterRepositoryPage } from '../../../models/interpreter-repository-page';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

type Repository = { id: string; url: string };

const unusedRepositoryId = (repositories: Repository[], name: string): string => {
  let id = name;
  while (repositories.some(repository => repository.id === id)) {
    id += '-unused';
  }
  return id;
};

const removableRepositories = (repositories: Repository[]) => {
  const target = {
    id: unusedRepositoryId(repositories, 'e2e-delete-target'),
    url: 'https://delete-target.example.test/maven/'
  };
  const retained = {
    id: unusedRepositoryId([...repositories, target], 'e2e-delete-retained'),
    url: 'https://delete-retained.example.test/maven/'
  };
  return { target, repositories: [...repositories, target, retained] };
};

const submissionCases = [
  {
    name: 'HTTP with snapshots disabled',
    protocol: 'http://' as const,
    id: 'http-repository',
    url: 'http.example.test/maven/',
    expectedUrl: 'http://http.example.test/maven/',
    snapshot: false,
    username: '',
    password: '',
    proxyProtocol: 'HTTP',
    proxyHost: '',
    proxyPort: null
  },
  {
    name: 'HTTPS with snapshots, repository credentials and a proxy',
    protocol: 'https://' as const,
    id: 'https-repository',
    url: 'secure.example.test/maven/',
    expectedUrl: 'https://secure.example.test/maven/',
    snapshot: true,
    username: 'repository-user',
    password: 'repository-secret',
    proxyProtocol: 'HTTPS',
    proxyHost: 'proxy.example.test',
    proxyPort: '8080'
  },
  {
    name: 'a file URL',
    protocol: 'file://' as const,
    id: 'file-repository',
    url: '/tmp/zeppelin-file-repository/',
    expectedUrl: 'file:///tmp/zeppelin-file-repository/',
    snapshot: false,
    username: '',
    password: '',
    proxyProtocol: 'HTTP',
    proxyHost: '',
    proxyPort: null
  }
];

test.describe('Interpreter repositories', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.INTERPRETER);
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.INTERPRETER_CREATE_REPO);

  let repositoryPage: InterpreterRepositoryPage;
  let modal: InterpreterRepositoryModal;
  let repositories: Repository[];

  test.beforeEach(async ({ page }) => {
    repositoryPage = new InterpreterRepositoryPage(page);
    modal = new InterpreterRepositoryModal(page);

    await test.step('Given the interpreter page loads the real server repository list', async () => {
      ({ repositories } = await openInterpreterPage(page));
      await expect(repositoryPage.repositoryTrigger).toBeVisible();
    });
  });

  test('opens and closes the repository panel with protected defaults and server repository tooltips', async ({
    interpreterWrites
  }) => {
    await test.step('Given the repository panel is initially closed', async () => {
      await expect(repositoryPage.panel).toBeHidden();
    });

    await test.step('When the Repository button is selected', async () => {
      await repositoryPage.repositoryTrigger.click();
    });

    await test.step('Then all server repositories are shown and defaults have no remove control', async () => {
      await expect(repositoryPage.heading).toBeVisible();
      await expect(repositoryPage.repositoryTags).toHaveText(repositories.map(repository => repository.id));
      await expect(repositoryPage.repositoryTag('central')).toBeVisible();
      await expect(repositoryPage.repositoryTag('local')).toBeVisible();
      await expect(repositoryPage.repositoryCloseIcon('central')).toHaveCount(0);
      await expect(repositoryPage.repositoryCloseIcon('local')).toHaveCount(0);
      await expect(repositoryPage.createRepositoryTag).toBeVisible();
    });

    await test.step('Then each repository tooltip shows its URL', async () => {
      await expect(repositoryPage.panel).toHaveCSS('opacity', '1');
      for (const repository of repositories) {
        await repositoryPage.repositoryTag(repository.id).hover();
        await expect(repositoryPage.tooltip).toHaveText(repository.url);
      }
    });

    await test.step('When the Repository button is selected again', async () => {
      await repositoryPage.repositoryTrigger.click();
    });

    await test.step('Then the panel closes without changing repositories', async () => {
      await expect(repositoryPage.panel).toBeHidden();
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('cancels deleting a custom repository without sending a mutation', async ({ page, interpreterWrites }) => {
    const deletion = removableRepositories(repositories);
    await stubInterpreterRead(page, '/api/interpreter/repository', deletion.repositories);
    await openInterpreterPage(page, 'reload');

    await test.step('Given the repository panel is open', async () => {
      await repositoryPage.repositoryTrigger.click();
      await expect(repositoryPage.repositoryTags).toHaveText(deletion.repositories.map(repository => repository.id));
      await expect(repositoryPage.panel).toHaveCSS('opacity', '1');
    });

    await test.step('When the custom repository remove control is selected', async () => {
      await repositoryPage.repositoryCloseIcon(deletion.target.id).click();
    });

    await test.step('Then confirmation identifies the repository before any mutation', async () => {
      await expect(repositoryPage.deleteDialog).toBeVisible();
      await expect(repositoryPage.deleteDialog).toContainText(deletion.target.id);
      await expect(repositoryPage.deleteMessage).toBeVisible();
      expect(interpreterWrites.requests).toEqual([]);
    });

    await test.step('When deletion is cancelled', async () => {
      await repositoryPage.cancelDeleteButton.click();
    });

    await test.step('Then every repository remains and no mutation was sent', async () => {
      await expect(repositoryPage.deleteDialog).toBeHidden();
      await expect(repositoryPage.repositoryTags).toHaveText(deletion.repositories.map(repository => repository.id));
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('confirms deletion with the repository ID and removes only the selected tag', async ({
    page,
    interpreterWrites
  }) => {
    const deletion = removableRepositories(repositories);
    await stubInterpreterRead(page, '/api/interpreter/repository', deletion.repositories);
    await openInterpreterPage(page, 'reload');

    await test.step('Given deletion is permitted only for the selected repository', async () => {
      await interpreterWrites.stub('DELETE', `/api/interpreter/repository/${deletion.target.id}`, '');
      await repositoryPage.repositoryTrigger.click();
      await expect(repositoryPage.repositoryTags).toHaveText(deletion.repositories.map(repository => repository.id));
      await expect(repositoryPage.panel).toHaveCSS('opacity', '1');
    });

    await test.step('When deletion of the custom repository is confirmed', async () => {
      await repositoryPage.repositoryCloseIcon(deletion.target.id).click();
      await expect(repositoryPage.deleteMessage).toBeVisible();
      await expect(repositoryPage.deleteDialog).toContainText(deletion.target.id);
      expect(interpreterWrites.requests).toEqual([]);
      await repositoryPage.confirmDeleteButton.click();
    });

    await test.step('Then exactly that repository is deleted and the other tags remain', async () => {
      await expect(repositoryPage.deleteDialog).toBeHidden();
      await expect(repositoryPage.repositoryTag(deletion.target.id)).toHaveCount(0);
      await expect(repositoryPage.repositoryTags).toHaveText(
        deletion.repositories
          .filter(repository => repository.id !== deletion.target.id)
          .map(repository => repository.id)
      );
      expect(interpreterWrites.requests).toHaveLength(1);
      expect(interpreterWrites.requests[0]).toMatchObject({
        method: 'DELETE',
        path: `/api/interpreter/repository/${deletion.target.id}`
      });
    });
  });

  test('requires both repository ID and URL before enabling Add', async ({ interpreterWrites }) => {
    await test.step('When the add repository modal is opened', async () => {
      await modal.openCreateModal();
    });

    await test.step('Then the empty required fields disable Add and defaults are visible', async () => {
      await expect(modal.dialog).toBeVisible();
      await expect(modal.idInput).toHaveValue('');
      await expect(modal.urlInput).toHaveValue('');
      await expect(modal.urlProtocol).toHaveText('http://');
      await expect(modal.snapshotSwitch).not.toHaveClass(/ant-switch-checked/);
      await expect(modal.proxyHttpRadio).toBeChecked();
      await expect(modal.proxyLoginInput).toBeVisible();
      await expect(modal.proxyPasswordInput).toHaveAttribute('type', 'password');
      await expect(modal.addButton).toBeDisabled();
    });

    await test.step('When only the ID is supplied', async () => {
      await modal.idInput.fill(unusedRepositoryId(repositories, 'required-fields-repository'));
    });

    await test.step('Then Add remains disabled until a URL is also supplied', async () => {
      await expect(modal.addButton).toBeDisabled();
      await modal.urlInput.fill('required.example.test/maven/');
      await expect(modal.addButton).toBeEnabled();
    });

    await test.step('When the ID is removed while the URL remains', async () => {
      await modal.idInput.fill('');
    });

    await test.step('Then a URL alone cannot enable Add or send a mutation', async () => {
      await expect(modal.addButton).toBeDisabled();
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('rejects an out-of-range proxy port and accepts port 8080', async ({ interpreterWrites }) => {
    await test.step('Given the required repository fields are complete', async () => {
      await modal.openCreateModal();
      await modal.idInput.fill(unusedRepositoryId(repositories, 'proxy-port-repository'));
      await modal.urlInput.fill('port.example.test/maven/');
      await expect(modal.addButton).toBeEnabled();
    });

    await test.step('When proxy port 99999 is entered', async () => {
      await modal.proxyPortInput.fill('99999');
    });

    await test.step('Then Add is disabled without submitting', async () => {
      await expect(modal.addButton).toBeDisabled();
      expect(interpreterWrites.requests).toEqual([]);
    });

    await test.step('When the proxy port is corrected to 8080', async () => {
      await modal.proxyPortInput.fill('8080');
    });

    await test.step('Then Add is enabled without sending a mutation', async () => {
      await expect(modal.addButton).toBeEnabled();
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  for (const submission of submissionCases) {
    test(`submits ${submission.name} and renders the refreshed server repository list`, async ({
      page,
      interpreterWrites
    }) => {
      const submittedId = unusedRepositoryId(repositories, submission.id);
      const serverRepository = {
        id: unusedRepositoryId(
          [...repositories, { id: submittedId, url: submission.expectedUrl }],
          'server-only-result'
        ),
        url: `https://server.example.test/${submission.id}/`
      };

      await test.step('Given the add repository modal is open with the initial repository list', async () => {
        await modal.openCreateModal();
        await expect(repositoryPage.repositoryTags).toHaveText(repositories.map(repository => repository.id));
        await interpreterWrites.stub('POST', '/api/interpreter/repository', '');
      });

      await test.step('When the repository URL and optional settings are entered', async () => {
        await modal.idInput.fill(submittedId);
        await modal.urlInput.fill(submission.url);
        await modal.selectUrlProtocol(submission.protocol);
        await expect(modal.urlProtocol).toHaveText(submission.protocol);
        await expect(modal.snapshotSwitch).not.toHaveClass(/ant-switch-checked/);
        if (submission.snapshot) {
          await modal.snapshotSwitch.click();
        }
        await expect(modal.snapshotSwitch).toHaveClass(
          submission.snapshot ? /ant-switch-checked/ : /^(?!.*ant-switch-checked)/
        );
        await modal.usernameInput.fill(submission.username);
        await modal.passwordInput.fill(submission.password);
        await expect(modal.passwordInput).toHaveAttribute('type', 'password');
        if (submission.proxyProtocol === 'HTTPS') {
          await modal.selectProxyProtocol('HTTPS');
        }
        await expect(modal.proxyHttpsRadio).toBeChecked({ checked: submission.proxyProtocol === 'HTTPS' });
        await expect(modal.proxyHttpRadio).toBeChecked({ checked: submission.proxyProtocol === 'HTTP' });
        await modal.proxyHostInput.fill(submission.proxyHost);
        if (submission.proxyPort !== null) {
          await modal.proxyPortInput.fill(submission.proxyPort);
        }
        await expect(modal.addButton).toBeEnabled();
      });

      await test.step('When Add succeeds and the next repository GET returns a new server-only tag', async () => {
        await stubInterpreterRead(page, '/api/interpreter/repository', [...repositories, serverRepository]);
        const refreshedRepositories = page.waitForResponse(
          response =>
            response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/interpreter/repository'
        );
        await modal.submit();
        await refreshedRepositories;
      });

      await test.step('Then the independently expected payload is sent and the refreshed response is rendered', async () => {
        await expect(modal.dialog).toBeHidden();
        expect(interpreterWrites.requests).toEqual([
          {
            method: 'POST',
            path: '/api/interpreter/repository',
            body: {
              id: submittedId,
              url: submission.expectedUrl,
              snapshot: submission.snapshot,
              username: submission.username,
              password: submission.password,
              proxyProtocol: submission.proxyProtocol,
              proxyHost: submission.proxyHost,
              proxyPort: submission.proxyPort,
              proxyLogin: '',
              proxyPassword: ''
            }
          }
        ]);
        await expect(repositoryPage.repositoryTags).toHaveText([
          ...repositories.map(repository => repository.id),
          serverRepository.id
        ]);
        await expect(repositoryPage.repositoryTag(submittedId)).toHaveCount(0);
        await expect(repositoryPage.panel).toHaveCSS('opacity', '1');
        await repositoryPage.repositoryTag(serverRepository.id).hover();
        await expect(repositoryPage.tooltip).toHaveText(serverRepository.url);
      });
    });
  }

  test('cancels a completed add form without changing repositories or sending a mutation', async ({
    interpreterWrites
  }) => {
    const cancelledId = unusedRepositoryId(repositories, 'cancelled-repository');

    await test.step('Given a valid add repository form has unsaved changes', async () => {
      await modal.openCreateModal();
      await expect(repositoryPage.repositoryTags).toHaveText(repositories.map(repository => repository.id));
      await modal.idInput.fill(cancelledId);
      await modal.urlInput.fill('cancelled.example.test/maven/');
      await modal.selectUrlProtocol('https://');
      await modal.snapshotSwitch.click();
      await modal.usernameInput.fill('unsaved-user');
      await modal.passwordInput.fill('unsaved-password');
      await modal.selectProxyProtocol('HTTPS');
      await modal.proxyHostInput.fill('unsaved-proxy.example.test');
      await modal.proxyPortInput.fill('8080');
      await expect(modal.addButton).toBeEnabled();
    });

    await test.step('When Cancel is selected', async () => {
      await modal.dismissButton.click();
    });

    await test.step('Then the modal closes and the original repositories remain without a mutation', async () => {
      await expect(modal.dialog).toBeHidden();
      await expect(repositoryPage.repositoryTags).toHaveText(repositories.map(repository => repository.id));
      await expect(repositoryPage.repositoryTag(cancelledId)).toHaveCount(0);
      expect(interpreterWrites.requests).toEqual([]);
    });
  });
});
