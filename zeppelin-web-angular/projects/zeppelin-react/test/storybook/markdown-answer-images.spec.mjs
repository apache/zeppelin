/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { readFileSync } from 'node:fs';
import { test, expect } from '@playwright/test';

const fixture = readFileSync(new URL('../fixtures/markdown-answer-image.svg', import.meta.url));
const source = 'https://images.example.test/plot.svg?q=SYNTHETIC_NOTEBOOK_VALUE';
const storyId = 'assistant-markdownanswer--image-loading';
const includesText = value => new RegExp(value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));

async function interceptImages(page, baseURL) {
  const requests = [];
  const origin = new URL(baseURL).origin;
  const isFixture = url => url.pathname.includes('markdown-answer-image') && url.pathname.endsWith('.svg');
  const isObserved = url => /^https?:$/.test(url.protocol) && (url.origin !== origin || isFixture(url));
  page.on('request', request => {
    if (isObserved(new URL(request.url()))) requests.push(request.url());
  });
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.hostname === 'images.example.test' || isFixture(url)) {
      await route.fulfill({ status: 200, contentType: 'image/svg+xml', body: fixture });
    } else if (url.origin !== origin) {
      // Unexpected external requests fail the assertions without contacting a real server.
      await route.abort();
    } else {
      await route.continue();
    }
  });
  return requests;
}

async function openImages(page) {
  await page.goto(`/iframe.html?id=${storyId}&viewMode=story`);
  const root = page.locator('#storybook-root');
  await expect(root.getByRole('button', { name: /load image/i }).first()).toBeVisible();
  return root;
}

async function setContent(page, content) {
  await page.evaluate(
    ({ id, content }) => {
      window.__STORYBOOK_PREVIEW__.channel.emit('updateStoryArgs', { storyId: id, updatedArgs: { content } });
    },
    { id: storyId, content }
  );
}

async function expectLoadedImage(image, url) {
  await expect(image).toHaveAttribute('src', url);
  await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
}

test('only keyboard-confirmed destinations are requested, including after a query change', async ({
  page,
  baseURL
}) => {
  const requests = await interceptImages(page, baseURL);
  const root = await openImages(page);
  await setContent(page, `Revenue improved.\n\n![Quarterly revenue chart](${source})`);
  const load = root.getByRole('button', { name: /load image/i });
  await expect(load).toHaveAccessibleDescription(includesText(source));
  await expect(root.locator('img, [srcset], link[rel="preload"]')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([]);

  await load.focus();
  await load.press('Enter');
  await expectLoadedImage(root.getByRole('img', { name: 'Quarterly revenue chart' }), source);
  expect(requests).toEqual([source]);

  const revised = source.replace('SYNTHETIC_NOTEBOOK_VALUE', 'UPDATED_NOTEBOOK_VALUE');
  await setContent(page, `The revised quarter includes final sales.\n\n![Quarterly revenue chart](${revised})`);
  await expect(load).toHaveAccessibleDescription(includesText(revised));
  await expect(root.getByRole('img')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([source]);
  await load.focus();
  await load.press('Space');
  await expectLoadedImage(root.getByRole('img', { name: 'Quarterly revenue chart' }), revised);
  expect(requests).toEqual([source, revised]);
});

test('streaming reference images preserve approval on append and require approval for a changed destination', async ({
  page,
  baseURL
}) => {
  const requests = await interceptImages(page, baseURL);
  await page.goto('/iframe.html?id=assistant-markdownanswer--streaming-images&viewMode=story');
  const root = page.locator('#storybook-root');
  const load = root.getByRole('button', { name: /load image/i });
  await expect(load).toBeVisible();
  const descriptionId = await load.getAttribute('aria-describedby');
  const disclosed = await page.locator(`[id="${descriptionId}"]`).textContent();
  await expect(root.getByRole('img')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([]);
  await load.press('Enter');
  const image = root.getByRole('img', { name: 'Quarterly revenue chart' });
  await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
  const sourceA = await image.getAttribute('src');
  expect(disclosed).toContain(sourceA);
  expect(requests).toEqual([sourceA]);

  const initialProgress = await root.getByRole('status').textContent();
  await root.getByRole('button', { name: 'Append next chunk', exact: true }).click();
  await expect(root.getByRole('status')).not.toHaveText(initialProgress);
  await expectLoadedImage(image, sourceA);
  await expect(load).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([sourceA]);

  const revised = new URL(sourceA);
  revised.searchParams.set('version', 'B');
  const sourceB = revised.href;
  await root.getByRole('button', { name: 'Append next chunk', exact: true }).click();
  await expect(load).toHaveAccessibleDescription(includesText(sourceB));
  await expect(image).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([sourceA]);
  await load.press('Space');
  await expectLoadedImage(image, sourceB);
  expect(requests).toEqual([sourceA, sourceB]);
});
