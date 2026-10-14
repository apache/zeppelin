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

const fixture = readFileSync(new URL('../../src/entities/assistant/ui/MarkdownAnswer.image.svg', import.meta.url));
const syntheticSource = 'https://images.example.test/plot.svg?q=SYNTHETIC_NOTEBOOK_VALUE';

async function interceptImages(page) {
  const requests = [];
  const isImageDestination = request => {
    const url = new URL(request.url());
    return (
      url.hostname === 'images.example.test' ||
      (url.pathname.includes('MarkdownAnswer.image') && url.pathname.endsWith('.svg'))
    );
  };
  // Capture every request type, including fetch, HEAD, preload and navigation.
  page.on('request', request => {
    if (isImageDestination(request)) requests.push(request.url());
  });
  await page.route('**/*', async route => {
    if (isImageDestination(route.request())) {
      await route.fulfill({ status: 200, contentType: 'image/svg+xml', body: fixture });
    } else {
      await route.continue();
    }
  });
  return requests;
}

async function expectLoadedImage(image, source) {
  await expect(image).toHaveAttribute('src', source);
  await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
}

test('ImageLoading sends no request before keyboard approval and resets approval for a changed query', async ({
  page
}) => {
  const requests = await interceptImages(page);
  const storyId = 'assistant-markdownanswer--image-loading';
  await page.goto(`/iframe.html?id=${storyId}&viewMode=story`);
  const root = page.locator('#storybook-root');
  await expect(root.getByRole('button', { name: /load image/i }).first()).toBeVisible();
  await page.waitForFunction(() => Boolean(window.__STORYBOOK_PREVIEW__?.channel));
  await page.evaluate(
    ({ storyId, source }) => {
      window.__STORYBOOK_PREVIEW__.channel.emit('updateStoryArgs', {
        storyId,
        updatedArgs: { content: `Revenue improved over the quarter.\n\n![Quarterly revenue chart](${source})` }
      });
    },
    { storyId, source: syntheticSource }
  );

  await expect(root.getByText(`Image URL: ${syntheticSource}`, { exact: true })).toBeVisible();
  await expect(root.getByText('Host: images.example.test', { exact: true })).toBeVisible();
  await expect(root.getByText(/loading.*sends a request.*information from.*answer/i)).toBeVisible();
  await expect(root.locator('img, [srcset], link[rel="preload"]')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([]);

  const load = root.getByRole('button', { name: /load image/i });
  await load.focus();
  await load.press('Enter');
  await expectLoadedImage(root.getByRole('img', { name: 'Quarterly revenue chart' }), syntheticSource);
  expect(requests).toEqual([syntheticSource]);

  const changedSource = syntheticSource.replace('SYNTHETIC_NOTEBOOK_VALUE', 'UPDATED_NOTEBOOK_VALUE');
  await page.evaluate(
    ({ storyId, source }) => {
      window.__STORYBOOK_PREVIEW__.channel.emit('updateStoryArgs', {
        storyId,
        updatedArgs: {
          content: `The revised quarter includes the final sales.\n\n![Quarterly revenue chart](${source})`
        }
      });
    },
    { storyId, source: changedSource }
  );
  await expect(root.getByText(`Image URL: ${changedSource}`, { exact: true })).toBeVisible();
  await expect(root.getByRole('img')).toHaveCount(0);
  await expect(load).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([syntheticSource]);

  await load.focus();
  await load.press('Space');
  await expectLoadedImage(root.getByRole('img', { name: 'Quarterly revenue chart' }), changedSource);
  expect(requests).toEqual([syntheticSource, changedSource]);
});

test('StreamingImages preserves unchanged approval and requires fresh approval for A to B to A', async ({ page }) => {
  const requests = await interceptImages(page);
  await page.goto('/iframe.html?id=assistant-markdownanswer--streaming-images&viewMode=story');
  const root = page.locator('#storybook-root');
  const load = root.getByRole('button', { name: /load image/i });
  await expect(load).toBeVisible();
  const sourceLabel = root.getByText(/^Image URL: https?:\/\/.*[?&]version=A$/);
  await expect(sourceLabel).toBeVisible();
  const sourceA = (await sourceLabel.textContent()).replace('Image URL: ', '');
  expect(new URL(sourceA).protocol).toMatch(/^https?:$/);
  await expect(root.getByRole('img')).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([]);

  await load.focus();
  await load.press('Enter');
  const image = root.getByRole('img', { name: 'Quarterly revenue chart' });
  await expectLoadedImage(image, sourceA);
  expect(requests).toEqual([sourceA]);

  await root.getByRole('button', { name: 'Append next chunk', exact: true }).click();
  await expect(root.getByRole('status')).toHaveText('Chunk 2 of 3');
  await expectLoadedImage(image, sourceA);
  await expect(load).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([sourceA]);

  await root.getByRole('button', { name: 'Append next chunk', exact: true }).click();
  await expect(root.getByRole('status')).toHaveText('Chunk 3 of 3');
  const sourceB = sourceA.replace('version=A', 'version=B');
  await expect(root.getByText(`Image URL: ${sourceB}`, { exact: true })).toBeVisible();
  await expect(image).toHaveCount(0);
  await expect(load).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([sourceA]);

  await load.focus();
  await load.press('Space');
  await expectLoadedImage(image, sourceB);
  expect(requests).toEqual([sourceA, sourceB]);

  await root.getByRole('button', { name: 'Restart stream', exact: true }).click();
  await expect(root.getByRole('status')).toHaveText('Chunk 1 of 3');
  await expect(root.getByText(`Image URL: ${sourceA}`, { exact: true })).toBeVisible();
  await expect(image).toHaveCount(0);
  await expect(load).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect(requests).toEqual([sourceA, sourceB]);

  await load.focus();
  await load.press('Enter');
  await expectLoadedImage(image, sourceA);
});
