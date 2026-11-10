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

import { expect, Page, Response, test as base } from '@playwright/test';
import type { Interpreter, InterpreterMap, InterpreterRepository } from '../../src/app/interfaces/interpreter';
import { waitForZeppelinReady } from '../utils';

export interface InterpreterPageData {
  settings: Interpreter[];
  availableInterpreters: InterpreterMap;
  repositories: Array<Pick<InterpreterRepository, 'id' | 'url'>>;
}

interface InterpreterWrites {
  requests: Array<{ method: string; path: string; body: unknown }>;
  stub(method: 'POST' | 'PUT' | 'DELETE', path: string, responseBody: unknown): Promise<void>;
}

const readBody = async <T>(response: Response): Promise<T> => {
  expect(response.ok(), `${response.request().method()} ${response.url()} returned ${response.status()}`).toBe(true);
  const payload: { status: string; body: T } = await response.json();
  expect(payload.status).toBe('OK');
  return payload.body;
};

export const openInterpreterPage = async (
  page: Page,
  navigation: 'direct' | 'reload' = 'direct'
): Promise<InterpreterPageData> => {
  const responseFor = (path: string): Promise<Response> =>
    page.waitForResponse(
      response => response.request().method() === 'GET' && new URL(response.url()).pathname === path
    );
  const settingsResponse = responseFor('/api/interpreter/setting');
  const availableResponse = responseFor('/api/interpreter');
  const repositoriesResponse = responseFor('/api/interpreter/repository');
  if (navigation === 'reload') {
    await page.reload();
  } else {
    await page.goto('/#/interpreter');
  }
  await waitForZeppelinReady(page);
  const [settings, availableInterpreters, repositories] = await Promise.all([
    settingsResponse.then(response => readBody<Interpreter[]>(response)),
    availableResponse.then(response => readBody<InterpreterMap>(response)),
    repositoriesResponse.then(response => readBody<InterpreterPageData['repositories']>(response))
  ]);
  expect(settings, 'The E2E server must provide at least one interpreter setting').not.toEqual([]);
  return { settings, availableInterpreters, repositories };
};

export const interpreterTitlePattern = (setting: Interpreter): RegExp => {
  const escape = (text: string): string => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const aliases = setting.interpreterGroup.map((group, index) =>
    escape(index === 0 ? `%${setting.name}` : `%${setting.name}.${group.name}`)
  );
  return new RegExp(`^\\s*${escape(setting.name)}\\s+${aliases.join('\\s*,\\s*')}\\s*$`);
};

export const stubInterpreterRead = async (page: Page, path: string, body: unknown): Promise<void> => {
  await page.route(`**${path}`, async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: { status: 'OK', message: '', body } });
    } else {
      await route.fallback();
    }
  });
};

export const test = base.extend<{ interpreterWrites: InterpreterWrites }>({
  interpreterWrites: [
    async ({ page }, use) => {
      const requests: InterpreterWrites['requests'] = [];
      const unexpectedWrites: string[] = [];
      await page.route(/\/api\/interpreter(?:\/|$)/, async route => {
        const request = route.request();
        if (request.method() === 'GET') {
          await route.fallback();
        } else {
          unexpectedWrites.push(`${request.method()} ${new URL(request.url()).pathname}`);
          await route.abort();
        }
      });
      await use({
        requests,
        stub: async (method, path, responseBody) => {
          await page.route(`**${path}`, async route => {
            const request = route.request();
            if (request.method() !== method) {
              await route.fallback();
              return;
            }
            requests.push({
              method: request.method(),
              path: new URL(request.url()).pathname,
              body: request.postDataJSON()
            });
            await route.fulfill({ json: { status: 'OK', message: '', body: responseBody } });
          });
        }
      });
      expect(unexpectedWrites, 'Only explicitly stubbed interpreter writes are allowed').toEqual([]);
    },
    { auto: true }
  ]
});

export { expect };
