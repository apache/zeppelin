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
const revisedSource = 'https://images.example.test/plot.svg?q=UPDATED_NOTEBOOK_VALUE';
const externalImageStory = 'external-image-approval';
const includesText = value => new RegExp(value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
const isFixture = url => url.pathname.includes('markdown-answer-image') && url.pathname.endsWith('.svg');
const observesImageRequest = (url, origin) =>
  /^https?:$/.test(url.protocol) && (url.origin !== origin || isFixture(url));

async function expectNoImageRequests(page, baseURL) {
  const origin = new URL(baseURL).origin;
  // Bound the quiet period from this point; networkidle may have been reached earlier.
  const unexpected = await page
    .waitForRequest(request => observesImageRequest(new URL(request.url()), origin), { timeout: 500 })
    .catch(error => {
      if (error.name !== 'TimeoutError') throw error;
      return undefined;
    });
  expect(unexpected?.url()).toBeUndefined();
}

async function interceptImages(page, baseURL, { failUrl, failures = 0 } = {}) {
  const requests = [];
  const origin = new URL(baseURL).origin;
  page.on('request', request => {
    if (observesImageRequest(new URL(request.url()), origin)) requests.push(request.url());
  });
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.href === failUrl && failures > 0) {
      failures--;
      await route.abort('failed');
    } else if (url.hostname === 'images.example.test' || isFixture(url)) {
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

async function openImageStory(page, story) {
  await page.goto(`/iframe.html?id=assistant-markdownanswer--${story}&viewMode=story`);
  return page.locator('#storybook-root');
}

function appendChunk(root) {
  return root.getByRole('button', { name: 'Append next chunk', exact: true });
}

function quarterlyImageControls(root) {
  return {
    load: root.getByRole('button', { name: 'Load image: Quarterly revenue chart' }),
    retry: root.getByRole('button', { name: 'Retry image: Quarterly revenue chart' }),
    image: root.getByRole('img', { name: 'Quarterly revenue chart' }),
    failure: root.getByRole('status').filter({ hasText: 'Image could not be loaded.' })
  };
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
  const root = await openImageStory(page, externalImageStory);
  const { load, image } = quarterlyImageControls(root);
  await expect(load).toHaveAccessibleDescription(includesText(source));
  await expect(root.locator('img, [srcset], link[rel="preload"]')).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([]);

  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Enter');
  await expectLoadedImage(image, source);
  expect(requests).toEqual([source]);

  await appendChunk(root).click();
  await expect(load).toHaveAccessibleDescription(includesText(revisedSource));
  await expect(root.getByRole('img')).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([source]);
  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Space');
  await expectLoadedImage(image, revisedSource);
  expect(requests).toEqual([source, revisedSource]);
});

test('a failed image retries the approved URL and clears the error after loading', async ({ page, baseURL }) => {
  const requests = await interceptImages(page, baseURL, { failUrl: source, failures: 2 });
  const root = await openImageStory(page, externalImageStory);
  const { load, retry, image, failure } = quarterlyImageControls(root);
  const description = root.getByText('Image description: Quarterly revenue chart');

  await expect(load).toHaveAccessibleDescription(includesText(source));
  await expect(image).toHaveCount(0);
  await expect(retry).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([]);

  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(failure).toBeVisible();
  await expect(description).toBeVisible();
  await expect(retry).toHaveAccessibleDescription(includesText(source));
  await expect(image).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([source]);

  await expect(retry).toBeFocused();
  await page.keyboard.press('Space');
  await expect.poll(() => requests.length).toBe(2);
  await expect(failure).toBeVisible();
  await expect(image).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([source, source]);

  await expect(retry).toBeFocused();
  await page.keyboard.press('Enter');
  await expectLoadedImage(image, source);
  await expect(failure).toHaveCount(0);
  await expect(retry).toHaveCount(0);
  await expect(description).toBeFocused();
  expect(requests).toEqual([source, source, source]);
});

test('streaming rekeys a focused retry without approving a same-URL sibling', async ({ page, baseURL }) => {
  const requests = await interceptImages(page, baseURL, { failUrl: revisedSource, failures: 1 });
  const root = await openImageStory(page, externalImageStory);
  const { load, retry, image, failure } = quarterlyImageControls(root);
  const siblingLoad = root.getByRole('button', { name: 'Load image: Earlier chart' });
  const siblingImage = root.getByRole('img', { name: 'Earlier chart' });
  const append = appendChunk(root);

  await append.click();
  await expect(load).toHaveAccessibleDescription(includesText(revisedSource));
  await expect(siblingLoad).toHaveCount(0);
  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(failure).toBeVisible();
  await expect(retry).toBeFocused();

  // Simulate a stream update without clicking away from the focused retry.
  await append.evaluate(button => button.click());
  await expect(root.getByText('More streamed text.')).toBeVisible();
  await expect(siblingLoad).toHaveAccessibleDescription(includesText(revisedSource));
  await expect(retry).toHaveAccessibleDescription(includesText(revisedSource));
  await expect(root.getByRole('img')).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([revisedSource]);

  await expect(retry).toBeFocused();
  await page.keyboard.press('Space');
  await expectLoadedImage(image, revisedSource);
  await expect(failure).toHaveCount(0);
  await expect(siblingLoad).toBeVisible();
  await expect(siblingImage).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([revisedSource, revisedSource]);
});

test('streaming reference images preserve approval on append and require approval for a changed destination', async ({
  page,
  baseURL
}) => {
  const requests = await interceptImages(page, baseURL);
  const root = await openImageStory(page, 'streaming-images');
  const load = root.getByRole('button', { name: /load image/i });
  await expect(load).toBeVisible();
  const descriptionId = await load.getAttribute('aria-describedby');
  const disclosed = await page.locator(`[id="${descriptionId}"]`).textContent();
  await expect(root.getByRole('img')).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([]);
  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Enter');
  const image = root.getByRole('img', { name: 'Quarterly revenue chart' });
  await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
  const sourceA = await image.getAttribute('src');
  expect(disclosed).toContain(sourceA);
  expect(requests).toEqual([sourceA]);

  const progress = root.getByRole('status').filter({ hasText: /^Chunk / });
  const initialProgress = await progress.textContent();
  await appendChunk(root).click();
  await expect(progress).not.toHaveText(initialProgress);
  await expectLoadedImage(image, sourceA);
  await expect(load).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([sourceA]);

  const revised = new URL(sourceA);
  revised.searchParams.set('version', 'B');
  const sourceB = revised.href;
  await appendChunk(root).click();
  await expect(load).toHaveAccessibleDescription(includesText(sourceB));
  await expect(image).toHaveCount(0);
  await expectNoImageRequests(page, baseURL);
  expect(requests).toEqual([sourceA]);
  await load.focus();
  await expect(load).toBeFocused();
  await page.keyboard.press('Space');
  await expectLoadedImage(image, sourceB);
  expect(requests).toEqual([sourceA, sourceB]);
});
