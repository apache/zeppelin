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

import { readFileSync } from 'node:fs';
import { test, expect } from '@playwright/test';

const { entries } = JSON.parse(readFileSync(new URL('../../storybook-static/index.json', import.meta.url), 'utf8'));
const stories = Object.values(entries).filter(entry => entry.type === 'story');
if (!stories.length) throw new Error('The Storybook build contains no stories');

for (const story of stories) {
  for (const theme of ['light', 'dark']) {
    test(`${story.id} (${theme})`, async ({ page }) => {
      const pageErrors = [];
      page.on('pageerror', error => pageErrors.push(error.message));
      await page.addInitScript(() => {
        window.storybookResult = { finished: [], errors: [] };
        const timer = setInterval(() => {
          const channel = window.__STORYBOOK_PREVIEW__?.channel;
          if (!channel) return;
          clearInterval(timer);
          channel.on('storyFinished', result => window.storybookResult.finished.push(result));
          for (const event of [
            'storyErrored',
            'storyThrewException',
            'playFunctionThrewException',
            'unhandledErrorsWhilePlaying'
          ]) {
            channel.on(event, () => window.storybookResult.errors.push(event));
          }
        }, 1);
      });
      await page.goto(`/iframe.html?id=${encodeURIComponent(story.id)}&viewMode=story&globals=theme:${theme}`);
      await page.waitForFunction(id => window.storybookResult.finished.some(result => result.storyId === id), story.id);
      const result = await page.evaluate(() => window.storybookResult);
      expect(result.finished.find(item => item.storyId === story.id).status).toBe('success');
      expect(result.errors).toEqual([]);
      expect(pageErrors).toEqual([]);
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expect(page.locator('#storybook-root')).not.toBeEmpty();
    });
  }
}
