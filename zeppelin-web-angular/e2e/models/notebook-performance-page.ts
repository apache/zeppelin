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

import { expect, Page } from '@playwright/test';

// JUSTIFIED: This fixed Angular baseline measures existing hosts and Monaco attachment,
// not a cross-framework user flow; these elements have no accessible role or test ID.
const selectors = {
  actionBar: 'zeppelin-notebook-action-bar',
  paragraphs: 'zeppelin-notebook-paragraph',
  editor: '.monaco-editor'
};

export class NotebookPerformancePage {
  constructor(private readonly page: Page) {}

  async measureReady() {
    // JUSTIFIED: Evaluate the three readiness signals together on the browser clock;
    // separate host-side assertions would add polling/IPC delay to the measured time.
    const ready = await this.page.waitForFunction(
      ({ actionBar, paragraphs, editor }) => {
        const bar = document.querySelector(actionBar);
        const style = bar && getComputedStyle(bar);
        if (
          !bar ||
          !bar.getClientRects().length ||
          style?.visibility === 'hidden' ||
          style?.display === 'none' ||
          document.querySelectorAll(paragraphs).length !== 100 ||
          !document.querySelector(`${paragraphs} ${editor}`)
        ) {
          return false;
        }
        return performance.now();
      },
      selectors,
      { timeout: 60000, polling: 'raf' }
    );
    const notebookReadyMs = (await ready.jsonValue()) as number;
    await ready.dispose();
    await expect(this.page.locator(selectors.actionBar)).toBeVisible();
    await expect(this.page.locator(selectors.paragraphs)).toHaveCount(100);
    // JUSTIFIED: The benchmark explicitly requires the first Monaco editor to be attached.
    await expect(this.page.locator(`${selectors.paragraphs} ${selectors.editor}`).first()).toBeAttached();
    const paint = await this.page.waitForFunction(
      () => {
        return performance.getEntriesByName('first-contentful-paint')[0]?.startTime || false;
      },
      undefined,
      { timeout: 10000 }
    );
    const fcpMs = (await paint.jsonValue()) as number;
    await paint.dispose();
    return { notebookReadyMs, fcpMs };
  }
}
