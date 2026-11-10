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

import type { Interpreter, InterpreterPropertyValue } from '../../../../src/app/interfaces/interpreter';
import {
  expect,
  InterpreterPageData,
  interpreterTitlePattern,
  openInterpreterPage,
  stubInterpreterRead,
  test
} from '../../../models/interpreter-test.util';
import { InterpreterItemPage, InterpreterPage } from '../../../models/interpreter-page';
import { addPageAnnotationBeforeEach, PAGES } from '../../../utils';

const unusedName = (names: string[], prefix: string): string => {
  let name = prefix;
  while (names.includes(name)) {
    name += '-new';
  }
  return name;
};

const typedSetting = (source: Interpreter): Interpreter => ({
  ...structuredClone(source),
  id: `${source.name}-different-id`,
  status: 'READY',
  option: {
    remote: true,
    port: -1,
    host: '',
    owners: [],
    perNote: 'shared',
    perUser: 'shared',
    isExistingProcess: false,
    setPermission: false,
    isUserImpersonate: false
  },
  properties: {
    greeting: { name: 'greeting', value: 'hello from server', type: 'string' },
    timeout: { name: 'timeout', value: '1000', type: 'number' },
    enabled: { name: 'enabled', value: false, type: 'checkbox' },
    secret: { name: 'secret', value: 'fixture-secret', type: 'password' },
    website: { name: 'website', value: 'https://example.org/', type: 'url' },
    script: { name: 'script', value: 'line one\nline two', type: 'textarea' }
  },
  dependencies: [
    { groupArtifactVersion: 'org.example:original:1.0', exclusions: ['org.example:excluded'], local: false }
  ]
});

const creationDefaults = (available: Interpreter[], group: string): Record<string, InterpreterPropertyValue> => {
  const properties: Record<string, InterpreterPropertyValue> = {};
  for (const interpreter of available.filter(entry => entry.group === group)) {
    for (const [name, property] of Object.entries(interpreter.properties)) {
      const value = property.defaultValue ?? '';
      properties[name] = {
        name,
        type: property.type,
        value: property.type === 'checkbox' ? value === true || value === 'true' : String(value)
      };
    }
  }
  return properties;
};

const creationGroup = (available: Interpreter[]): Interpreter => {
  const group = available.find(entry => Object.keys(entry.properties).length > 0);
  if (!group) {
    throw new Error('The E2E server must provide an available interpreter group with default properties');
  }
  return group;
};

test.describe('Interpreter Settings', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.INTERPRETER_ITEM);

  let interpreterPage: InterpreterPage;
  let item: InterpreterItemPage;
  let serverData: InterpreterPageData;
  let setting: Interpreter;

  test.beforeEach(async ({ page }) => {
    interpreterPage = new InterpreterPage(page);
    serverData = await openInterpreterPage(page);
    setting = serverData.settings[0];
    item = interpreterPage.setting(setting.name);
    await expect(interpreterPage.settingTitles).toHaveText(serverData.settings.map(interpreterTitlePattern));
  });

  test('shows typed values, ready and error states when those settings are returned', async ({ page }) => {
    const ready = typedSetting(setting);
    const failed = {
      ...structuredClone(setting),
      name: unusedName(
        serverData.settings.map(entry => entry.name),
        'e2e-failed-setting'
      ),
      status: 'ERROR',
      errorReason: 'Fixture dependency could not be downloaded'
    };
    await stubInterpreterRead(page, '/api/interpreter/setting', [ready, ...serverData.settings.slice(1), failed]);
    await openInterpreterPage(page, 'reload');

    await test.step('Then view mode renders each property type and disables editing', async () => {
      await expect(item.card.getByRole('heading', { name: 'Option', exact: true })).toBeVisible();
      await expect(item.card.getByRole('heading', { name: 'Properties', exact: true })).toBeVisible();
      await expect(item.card.getByRole('heading', { name: 'Dependencies', exact: true })).toBeVisible();
      await expect(item.propertyCell('greeting')).toHaveText('hello from server');
      await expect(item.propertyCell('timeout')).toHaveText('1000');
      await expect(item.propertyCell('enabled')).toHaveText('false');
      await expect(item.propertyCell('secret')).toHaveText('******');
      await expect(item.propertyCell('website').getByRole('link')).toHaveAttribute('href', 'https://example.org/');
      await expect(item.propertyCell('script')).toHaveText('line one line two');
      await expect(item.dependencyRow('org.example:original:1.0')).toContainText('org.example:excluded');
      await expect(item.properties.getByText('Action', { exact: true })).toHaveCount(0);
    });

    await test.step('Then the returned status and error reason are visible', async () => {
      await item.status.hover();
      await expect(item.tooltip).toHaveText('Ready');
      const errorItem = interpreterPage.setting(failed.name);
      await errorItem.status.hover();
      await expect(item.tooltip).toHaveText('Error downloading dependencies');
      await expect(errorItem.card.getByText(failed.errorReason, { exact: true })).toBeVisible();
    });
  });

  test('validates a new name against actual settings and populates an available group', async ({
    interpreterWrites
  }) => {
    const available = Object.values(serverData.availableInterpreters);
    const group = creationGroup(available);
    const defaults = creationDefaults(available, group.group);
    const form = interpreterPage.createForm();
    const name = unusedName(
      serverData.settings.map(entry => entry.name),
      'e2e-new-interpreter'
    );

    await test.step('When Create opens a blank form', async () => {
      await interpreterPage.createCard.click();
      await expect(form.card.getByRole('heading', { name: 'Create new interpreter', exact: true })).toBeVisible();
      await expect(interpreterPage.createCard).toHaveCount(0);
      await expect(form.save).toBeDisabled();
      await form.name.fill(name);
      await expect(form.save).toBeDisabled();
    });

    await test.step('Then a real available group supplies property rows and existing names are rejected', async () => {
      await form.selectGroup(group.name);
      await expect(form.card.getByRole('button', { name: 'Remove property', exact: true })).toHaveCount(
        Object.keys(defaults).length
      );
      for (const key of Object.keys(defaults)) {
        await expect(form.propertyRow(key)).toBeVisible();
      }
      await expect(form.save).toBeEnabled();
      await form.name.fill(setting.name);
      await form.name.blur();
      await expect(form.card.getByText(`Name '${setting.name}' already exists`, { exact: true })).toBeVisible();
      await expect(form.save).toBeDisabled();
      await form.name.clear();
      await expect(form.save).toBeDisabled();
    });

    await test.step('Then Cancel restores Create without changing the server-backed list or writing', async () => {
      await form.cancel.click();
      await expect(form.card).toHaveCount(0);
      await expect(interpreterPage.createCard).toBeVisible();
      await expect(interpreterPage.settingTitles).toHaveText(serverData.settings.map(interpreterTitlePattern));
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('submits real group defaults and renders the independently stubbed creation response', async ({
    interpreterWrites
  }) => {
    const available = Object.values(serverData.availableInterpreters);
    const group = creationGroup(available);
    const name = unusedName(
      serverData.settings.map(entry => entry.name),
      'e2e-created-interpreter'
    );
    const created: Interpreter = {
      ...structuredClone(setting),
      id: 'created-server-id',
      name,
      group: group.group,
      properties: {
        'e2e.server.response': { name: 'e2e.server.response', type: 'string', value: 'from mutation response' }
      }
    };
    await interpreterWrites.stub('POST', '/api/interpreter/setting', created);
    const form = interpreterPage.createForm();

    await test.step('When a real available group and an additional property are submitted', async () => {
      await interpreterPage.createCard.click();
      await form.name.fill(name);
      await form.selectGroup(group.name);
      await form.propertyKey.fill('e2e.entered.value');
      await form.propertyValue.fill('entered in browser');
      await form.card.getByRole('button', { name: 'Add property', exact: true }).click();
      await form.save.click();
    });

    await test.step('Then the request contains the real defaults and the new card uses the response', async () => {
      await expect(interpreterPage.setting(name).propertyCell('e2e.server.response')).toHaveText(
        'from mutation response'
      );
      await expect(form.card).toHaveCount(0);
      await expect(interpreterPage.createCard).toBeVisible();
      await expect(interpreterPage.settings).toHaveCount(serverData.settings.length + 1);
      expect(interpreterWrites.requests).toEqual([
        {
          method: 'POST',
          path: '/api/interpreter/setting',
          body: expect.objectContaining({
            name,
            group: group.group,
            properties: {
              ...creationDefaults(available, group.group),
              'e2e.entered.value': { name: 'e2e.entered.value', type: 'string', value: 'entered in browser' }
            },
            dependencies: []
          })
        }
      ]);
    });
  });

  test('Cancel restores every typed property on reopening', async ({ page, interpreterWrites }) => {
    const controlled = typedSetting(setting);
    await stubInterpreterRead(page, '/api/interpreter/setting', [controlled, ...serverData.settings.slice(1)]);
    await openInterpreterPage(page, 'reload');

    await test.step('When typed values and process options are changed without saving', async () => {
      await item.edit.click();
      await expect(item.properties.getByText('Action', { exact: true })).toBeVisible();
      await item.propertyCell('greeting').getByRole('textbox').fill('unsaved greeting');
      await item.propertyCell('timeout').getByRole('spinbutton').fill('1234');
      await item.propertyCell('enabled').getByRole('checkbox').check();
      await item.passwordValue('secret').fill('unsaved secret');
      await item.propertyCell('website').getByRole('textbox').fill('https://changed.example.org/');
      await item.propertyCell('script').getByRole('textbox').fill('unsaved script');
      await item.existingProcess.check();
      await item.host.fill('unsaved.example.org');
      await item.cancel.click();
    });

    await test.step('Then view and reopened edit mode retain the returned configuration', async () => {
      await expect(item.edit).toBeVisible();
      await expect(item.propertyCell('greeting')).toHaveText('hello from server');
      await item.edit.click();
      await expect(item.propertyCell('greeting').getByRole('textbox')).toHaveValue('hello from server');
      await expect(item.propertyCell('timeout').getByRole('spinbutton')).toHaveValue('1000');
      await expect(item.propertyCell('enabled').getByRole('checkbox')).not.toBeChecked();
      await expect(item.passwordValue('secret')).toHaveValue('fixture-secret');
      await expect(item.propertyCell('website').getByRole('textbox')).toHaveValue('https://example.org/');
      await expect(item.propertyCell('script').getByRole('textbox')).toHaveValue('line one\nline two');
      await expect(item.existingProcess).not.toBeChecked();
      await expect(item.host).toHaveCount(0);
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('reveals and submits process and permission fields on a real setting', async ({ interpreterWrites }) => {
    const owner = unusedName(setting.option.owners ?? [], 'e2e-owner');
    const path = `/api/interpreter/setting/${encodeURIComponent(setting.name)}`;
    await interpreterWrites.stub('PUT', path, setting);

    await test.step('When options on a returned setting are toggled in edit mode', async () => {
      await item.edit.click();
      await item.existingProcess.uncheck();
      await item.setPermission.uncheck();
      await expect(item.host).toHaveCount(0);
      await expect(item.port).toHaveCount(0);
      await expect(item.owners).toHaveCount(0);
      await item.existingProcess.check();
      await expect(item.host).toBeVisible();
      await expect(item.port).toBeVisible();
      await item.setPermission.check();
      await expect(item.owners.getByRole('textbox')).toBeVisible();
      await item.existingProcess.uncheck();
      await item.setPermission.uncheck();
      await expect(item.host).toHaveCount(0);
      await expect(item.port).toHaveCount(0);
      await expect(item.owners).toHaveCount(0);
    });

    await test.step('Then the entered host, valid port and owner are submitted without persisting', async () => {
      await item.existingProcess.check();
      await item.host.fill('worker.example.org');
      await item.port.fill('99999');
      await expect(item.save).toBeDisabled();
      await item.port.fill('8080');
      await item.setPermission.check();
      await item.owners.getByRole('textbox').fill(`${owner},`);
      await expect(item.owners.getByText(owner, { exact: true })).toBeVisible();
      await expect(item.save).toBeEnabled();
      await item.save.click();
      await expect(item.edit).toBeVisible();
      await expect
        .poll(() => interpreterWrites.requests)
        .toEqual([
          {
            method: 'PUT',
            path,
            body: expect.objectContaining({
              option: expect.objectContaining({
                isExistingProcess: true,
                host: 'worker.example.org',
                port: 8080,
                setPermission: true,
                owners: [...(setting.option.owners ?? []), owner]
              })
            })
          }
        ]);
    });
  });

  test('adds, updates and removes properties and dependencies locally', async ({ page, interpreterWrites }) => {
    const controlled: Interpreter = {
      ...structuredClone(setting),
      dependencies: [
        { groupArtifactVersion: 'org.example:original:1.0', exclusions: ['org.example:excluded'], local: false }
      ]
    };
    await stubInterpreterRead(page, '/api/interpreter/setting', [controlled, ...serverData.settings.slice(1)]);
    await openInterpreterPage(page, 'reload');

    await test.step('When a property and dependency are added in edit mode', async () => {
      await item.edit.click();
      await item.propertyKey.fill('custom.property');
      await item.propertyValue.fill('first value');
      await item.card.getByRole('button', { name: 'Add property', exact: true }).click();
      await expect(item.propertyCell('custom.property').getByRole('textbox')).toHaveValue('first value');
      await item.propertyKey.fill('custom.property');
      await item.selectPropertyType('number');
      await item.newProperty.getByRole('spinbutton').fill('42');
      await item.card.getByRole('button', { name: 'Add property', exact: true }).click();
      await expect(item.propertyRow('custom.property')).toHaveCount(1);
      await expect(item.propertyCell('custom.property').getByRole('spinbutton')).toHaveValue('42');
      await item.artifact.fill('org.example:added:2.0');
      await item.exclusions.fill('org.example:omit');
      await item.card.getByRole('button', { name: 'Add dependency', exact: true }).click();
      await expect(item.editableDependencyRow(1).getByRole('textbox').nth(0)).toHaveValue('org.example:added:2.0');
      await expect(item.editableDependencyRow(1).getByRole('textbox').nth(1)).toHaveValue('org.example:omit');
    });

    await test.step('Then removal discards only added entries without a write', async () => {
      await item.propertyRow('custom.property').getByRole('button', { name: 'Remove property', exact: true }).click();
      await expect(item.propertyRow('custom.property')).toHaveCount(0);
      await item.editableDependencyRow(1).getByRole('button', { name: 'Remove dependency', exact: true }).click();
      await expect(item.dependencies.getByRole('textbox')).toHaveCount(4);
      await item.cancel.click();
      await expect(item.dependencyRow('org.example:original:1.0')).toContainText('org.example:excluded');
      expect(interpreterWrites.requests).toEqual([]);
    });
  });

  test('updates by name rather than ID with typed properties and edited dependencies', async ({
    page,
    interpreterWrites
  }) => {
    const controlled = typedSetting(setting);
    await stubInterpreterRead(page, '/api/interpreter/setting', [controlled, ...serverData.settings.slice(1)]);
    await openInterpreterPage(page, 'reload');
    const updated = structuredClone(controlled);
    updated.properties.greeting.value = 'submitted greeting';
    const path = `/api/interpreter/setting/${encodeURIComponent(controlled.name)}`;
    await interpreterWrites.stub('PUT', path, updated);

    await test.step('When changed values, an edited dependency and a pending addition are saved', async () => {
      await item.edit.click();
      await item.propertyCell('greeting').getByRole('textbox').fill('submitted greeting');
      await item.propertyCell('timeout').getByRole('spinbutton').fill('60000');
      await item.propertyCell('enabled').getByRole('checkbox').check();
      await item.editableDependencyRow(0).getByRole('textbox').nth(0).fill('org.example:edited:4.0');
      await item.editableDependencyRow(0).getByRole('textbox').nth(1).fill('org.example:edited-exclusion');
      await item.artifact.fill('org.example:new:3.0');
      await item.exclusions.fill(' org.example:first, , org.example:second ');
      const response = page.waitForResponse(
        result => new URL(result.url()).pathname === path && result.request().method() === 'PUT'
      );
      await item.save.click();
      await (await response).finished();
    });

    await test.step('Then submitted values remain editable and the exact request targets the name', async () => {
      await expect(item.propertyCell('greeting')).toHaveText('submitted greeting');
      await item.edit.click();
      await expect(item.propertyCell('greeting').getByRole('textbox')).toHaveValue('submitted greeting');
      expect(interpreterWrites.requests).toEqual([
        {
          method: 'PUT',
          path,
          body: {
            option: expect.objectContaining({ perNote: 'shared', perUser: 'shared', remote: true }),
            properties: {
              greeting: { name: 'greeting', type: 'string', value: 'submitted greeting' },
              timeout: { name: 'timeout', type: 'number', value: '60000' },
              enabled: { name: 'enabled', type: 'checkbox', value: true },
              secret: { name: 'secret', type: 'password', value: 'fixture-secret' },
              website: { name: 'website', type: 'url', value: 'https://example.org/' },
              script: { name: 'script', type: 'textarea', value: 'line one\nline two' }
            },
            dependencies: [
              { groupArtifactVersion: 'org.example:edited:4.0', exclusions: ['org.example:edited-exclusion'] },
              { groupArtifactVersion: 'org.example:new:3.0', exclusions: ['org.example:first', 'org.example:second'] }
            ]
          }
        }
      ]);
    });
  });

  for (const principal of ['anonymous', 'fixture-user']) {
    test(`exposes and submits binding choices for an isolated ${principal} ticket`, async ({
      page,
      interpreterWrites
    }) => {
      await page.route('**/api/security/ticket', route =>
        route.fulfill({
          json: {
            status: 'OK',
            message: '',
            body: {
              principal,
              ticket: principal === 'anonymous' ? 'anonymous' : 'fixture-ticket',
              roles: principal === 'anonymous' ? '[]' : '["users"]'
            }
          }
        })
      );
      await openInterpreterPage(page, 'reload');
      await item.edit.click();

      await test.step('When binding choices are shown for the selected identity', async () => {
        await item.bindingMode.click();
        await expect(item.bindingMenu.getByText('Globally', { exact: true })).toBeVisible();
        await expect(item.bindingMenu.getByText('Per Note', { exact: true })).toBeVisible();
        await expect(item.bindingMenu.getByText('Per User', { exact: true })).toHaveCount(
          principal === 'anonymous' ? 0 : 1
        );
        await item.bindingMenu.getByText('Per Note', { exact: true }).click();
        await expect(item.bindingMode).toContainText('Per Note');
        await item.card.getByText('scoped', { exact: true }).click();
        await item.bindingMenu.getByText('isolated per note', { exact: true }).click();
        await expect(item.card.getByText('isolated', { exact: true })).toBeVisible();
      });

      await test.step('Then per-user isolation controls follow the identity capability', async () => {
        if (principal !== 'anonymous') {
          await item.bindingMode.click();
          await item.bindingMenu.getByText('Per User', { exact: true }).click();
          await item.card.getByText('scoped', { exact: true }).click();
          await item.bindingMenu.getByText('isolated per user', { exact: true }).click();
        }
        await expect(item.card.getByRole('checkbox', { name: 'User Impersonate', exact: true })).toHaveCount(
          principal === 'anonymous' ? 0 : 1
        );
        await expect(item.card.getByRole('button', { name: 'Add per-note process', exact: true })).toHaveCount(
          principal === 'anonymous' ? 0 : 1
        );
      });

      await test.step('Then isolation and optional impersonation are sent in the intercepted request', async () => {
        if (principal !== 'anonymous') {
          await item.card.getByRole('checkbox', { name: 'User Impersonate', exact: true }).check();
          await item.card.getByRole('button', { name: 'Add per-note process', exact: true }).click();
        }
        await expect(item.card.getByRole('button', { name: 'Remove per-note process', exact: true })).toHaveCount(
          principal === 'anonymous' ? 0 : 1
        );
        if (principal !== 'anonymous') {
          await item.card.getByRole('button', { name: 'Remove per-note process', exact: true }).click();
        }
        await expect(item.card.getByRole('button', { name: 'Add per-note process', exact: true })).toHaveCount(
          principal === 'anonymous' ? 0 : 1
        );
        const path = `/api/interpreter/setting/${encodeURIComponent(setting.name)}`;
        await interpreterWrites.stub('PUT', path, setting);
        await item.save.click();
        await expect(item.edit).toBeVisible();
        await expect
          .poll(() => interpreterWrites.requests)
          .toEqual([
            {
              method: 'PUT',
              path,
              body: expect.objectContaining({
                option: expect.objectContaining({
                  perNote: principal === 'anonymous' ? 'isolated' : 'shared',
                  perUser: principal === 'anonymous' ? '' : 'isolated',
                  isUserImpersonate: principal !== 'anonymous'
                })
              })
            }
          ]);
      });
    });
  }

  test('cancels and confirms restart of a real setting without executing it', async ({ page, interpreterWrites }) => {
    const dialog = page.getByRole('dialog');

    await test.step('When Restart is cancelled, no request is dispatched', async () => {
      await item.restart.click();
      await expect(dialog).toContainText('Restart Interpreter');
      await expect(dialog).toContainText('Do you want to restart this interpreter?');
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
      await expect(dialog).toHaveCount(0);
      expect(interpreterWrites.requests).toEqual([]);
    });

    await test.step('Then confirmation targets the returned name and shows the acknowledgement', async () => {
      const path = `/api/interpreter/setting/restart/${encodeURIComponent(setting.name)}`;
      await interpreterWrites.stub('PUT', path, null);
      await item.restart.click();
      await dialog.getByRole('button', { name: 'OK', exact: true }).click();
      await expect(
        page.getByText('Interpreter stopped. Will be lazily started on next run.', { exact: true })
      ).toBeVisible();
      await expect(interpreterPage.settingTitles).toHaveText(serverData.settings.map(interpreterTitlePattern));
      expect(interpreterWrites.requests).toEqual([{ method: 'PUT', path, body: null }]);
    });
  });

  test('cancels and confirms removal of the selected real setting', async ({ page, interpreterWrites }) => {
    const dialog = page.getByRole('dialog');

    await test.step('When Remove is cancelled, the setting remains without a request', async () => {
      await item.remove.click();
      await expect(dialog).toContainText('Remove Interpreter');
      await expect(dialog).toContainText('Do you want to delete this interpreter setting?');
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(item.card).toBeVisible();
      expect(interpreterWrites.requests).toEqual([]);
    });

    await test.step('Then confirmation removes only that card using an intercepted DELETE', async () => {
      const path = `/api/interpreter/setting/${encodeURIComponent(setting.name)}`;
      await interpreterWrites.stub('DELETE', path, null);
      await item.remove.click();
      await dialog.getByRole('button', { name: 'OK', exact: true }).click();
      await expect(item.card).toHaveCount(0);
      await expect(interpreterPage.settingTitles).toHaveText(serverData.settings.slice(1).map(interpreterTitlePattern));
      expect(interpreterWrites.requests).toEqual([{ method: 'DELETE', path, body: null }]);
    });
  });
});
