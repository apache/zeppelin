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
import { BasePage } from './base-page';

export class JobManagerPage extends BasePage {
  readonly pageHeader: Locator;
  readonly pageTitle: Locator;
  readonly pageDescription: Locator;
  readonly searchInput: Locator;
  readonly interpreterSelect: Locator;
  readonly sortSelect: Locator;
  readonly totalCounter: Locator;
  readonly statusLegend: Locator;
  readonly loadingSkeleton: Locator;
  readonly disabledAlert: Locator;
  readonly emptyState: Locator;
  readonly jobCards: Locator;
  readonly jobStatusBadges: Locator;

  constructor(page: Page) {
    super(page);
    this.pageHeader = page.locator('zeppelin-page-header');
    this.pageTitle = this.pageHeader.getByText('Job');
    this.pageDescription = this.pageHeader.locator('p');
    this.searchInput = page.getByPlaceholder('Search jobs...');
    this.interpreterSelect = page
      .locator('nz-form-label')
      .filter({ hasText: 'Interpreter' })
      .locator('..')
      .locator('nz-select');
    this.sortSelect = page
      .locator('nz-form-label')
      .filter({ hasText: 'Sort' })
      .locator('..')
      .locator('nz-select');
    this.totalCounter = page.locator('nz-form-text');
    this.statusLegend = page.locator('.status-legend');
    this.loadingSkeleton = page.locator('nz-card nz-skeleton');
    this.disabledAlert = page.locator('nz-alert[nzType="info"]');
    this.emptyState = page.locator('nz-empty');
    this.jobCards = page.locator('zeppelin-job-manager-job');
    this.jobStatusBadges = page.locator('zeppelin-job-manager-job-status');
  }

  async navigate(): Promise<void> {
    await this.page.goto('/#/jobmanager');
    await this.waitForPageLoad();
  }

  async waitForPageLoad(): Promise<void> {
    await super.waitForPageLoad();
    await this.page.waitForFunction(
      () => {
        const skeleton = document.querySelector('nz-card nz-skeleton');
        return !skeleton || skeleton.getAttribute('nzActive') !== 'true';
      },
      { timeout: 10000 }
    );
  }

  async searchJobs(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await this.page.waitForTimeout(500);
  }

  async selectInterpreter(interpreter: string): Promise<void> {
    await this.interpreterSelect.click();
    await this.page
      .locator('nz-option-item')
      .filter({ hasText: interpreter })
      .click();
    await this.page.waitForTimeout(500);
  }

  async selectSort(sortOption: string): Promise<void> {
    await this.sortSelect.click();
    await this.page
      .locator('nz-option-item')
      .filter({ hasText: sortOption })
      .click();
    await this.page.waitForTimeout(500);
  }

  async getTotalCount(): Promise<number> {
    const text = await this.totalCounter.textContent();
    return parseInt(text || '0', 10);
  }

  async isLoading(): Promise<boolean> {
    return await this.loadingSkeleton.isVisible();
  }

  async isDisabled(): Promise<boolean> {
    return await this.disabledAlert.isVisible();
  }

  async getDisabledMessage(): Promise<string | null> {
    return await this.disabledAlert.textContent();
  }

  async isEmpty(): Promise<boolean> {
    return await this.emptyState.isVisible();
  }

  async getJobCardCount(): Promise<number> {
    return await this.jobCards.count();
  }

  async getStatusBadgeCount(): Promise<number> {
    return await this.jobStatusBadges.count();
  }

  getJobCard(index: number): Locator {
    return this.jobCards.nth(index);
  }

  getJobCardByNoteName(noteName: string): Locator {
    return this.jobCards.filter({ hasText: noteName });
  }

  async clearFilters(): Promise<void> {
    await this.searchInput.clear();
    await this.selectInterpreter('All');
  }

  async getJobNoteNames(): Promise<string[]> {
    const names: string[] = [];
    const count = await this.getJobCardCount();
    for (let i = 0; i < count; i++) {
      const card = this.getJobCard(i);
      const nameElement = card.locator('a[routerLink]').first();
      const name = await nameElement.textContent();
      if (name) {
        names.push(name.trim());
      }
    }
    return names;
  }
}
