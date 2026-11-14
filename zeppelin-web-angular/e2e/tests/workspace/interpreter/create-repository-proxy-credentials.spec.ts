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

import { expect, openInterpreterPage, test } from '../../../models/interpreter-test.util';
import { InterpreterRepositoryModal } from '../../../models/interpreter-repository-modal';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

interface AddRepositoryRequestBody {
  id: string;
  proxyLogin?: string;
  proxyPassword?: string;
}

test.describe('Interpreter Repository - Proxy Credentials', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.INTERPRETER_CREATE_REPO);

  test('submits the proxy login and proxy password to their own fields', async ({ page, interpreterWrites }) => {
    const proxyLogin = 'proxy-user';
    const proxyPassword = 'proxy-secret';
    const repoId = `e2e-proxy-repo-${Date.now()}`;

    await openInterpreterPage(page);
    await interpreterWrites.stub('POST', '/api/interpreter/repository', '');

    const modal = new InterpreterRepositoryModal(page);
    await modal.openCreateModal();
    await modal.fillProxyRepository({
      id: repoId,
      url: 'repo1.maven.org/maven2/',
      proxyLogin,
      proxyPassword
    });
    await modal.submit();

    await expect.poll(() => interpreterWrites.requests.length).toBe(1);
    const requestBody = interpreterWrites.requests[0].body as AddRepositoryRequestBody;

    // Regression guard for ZEPPELIN-6520: Password was bound to proxyLogin, so the
    // password overwrote the login and proxyPassword was always empty.
    expect(requestBody.proxyLogin).toBe(proxyLogin);
    expect(requestBody.proxyPassword).toBe(proxyPassword);
  });
});
