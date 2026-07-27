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

import { Locator, Page } from '@playwright/test';
import { waitForZeppelinReady } from '../utils';
import { BasePage } from './base-page';

export class JobManagerPage extends BasePage {
  readonly searchInput: Locator;
  readonly jobItems: Locator;

  constructor(page: Page) {
    super(page);
    this.searchInput = page.locator('input[placeholder="Search jobs..."]');
    this.jobItems = page.locator('zeppelin-job-manager-job');
  }

  async navigate(): Promise<void> {
    await this.navigateToRoute('/jobmanager', { timeout: 60000 });
    await this.page.waitForURL('**/#/jobmanager', { timeout: 60000 });
    await waitForZeppelinReady(this.page);
  }

  jobItemByName(noteName: string): Locator {
    return this.jobItems.filter({ hasText: noteName });
  }

  async filterByNoteName(noteName: string): Promise<void> {
    await this.fillAndVerifyInput(this.searchInput, noteName);
  }

  async clearNoteNameFilter(): Promise<void> {
    await this.searchInput.fill('');
  }
}
