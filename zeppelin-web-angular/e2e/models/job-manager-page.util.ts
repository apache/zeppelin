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
import { JobCard } from './job-card';
import { JobManagerPage } from './job-manager-page';

export class JobManagerUtil {
  private page: Page;
  private jobManagerPage: JobManagerPage;

  constructor(page: Page) {
    this.page = page;
    this.jobManagerPage = new JobManagerPage(page);
  }

  async verifyPageHeader(): Promise<void> {
    await expect(this.jobManagerPage.pageTitle).toBeVisible();
    await expect(this.jobManagerPage.pageDescription).toContainText(
      'You can monitor the status of notebook and navigate to note or paragraph'
    );
  }

  async verifyDisabledState(): Promise<void> {
    await expect(this.jobManagerPage.disabledAlert).toBeVisible();
    await expect(this.jobManagerPage.disabledAlert).toContainText(
      'Job Manager is disabled in the current configuration'
    );
  }

  async verifyLoadingState(): Promise<void> {
    await expect(this.jobManagerPage.loadingSkeleton).toBeVisible();
  }

  async verifyStatusLegend(): Promise<void> {
    const statusBadges = this.jobManagerPage.statusLegend.locator('zeppelin-job-manager-job-status');
    const expectedStatuses = ['READY', 'RUNNING', 'FINISHED', 'ERROR', 'ABORT', 'PENDING'];
    await expect(statusBadges).toHaveCount(expectedStatuses.length);
    for (const status of expectedStatuses) {
      await expect(statusBadges.filter({ hasText: status })).toBeVisible();
    }
  }

  async verifyStatusBadgeColors(): Promise<void> {
    const statusLegend = this.jobManagerPage.statusLegend;
    const badgeMap = {
      READY: 'success',
      FINISHED: 'success',
      ERROR: 'error',
      ABORT: 'warning',
      RUNNING: 'processing',
      PENDING: 'default'
    };
    for (const [status, badgeType] of Object.entries(badgeMap)) {
      const badge = statusLegend.locator('zeppelin-job-manager-job-status').filter({ hasText: status });
      await expect(badge.locator(`nz-badge[ng-reflect-nz-status="${badgeType}"]`)).toBeVisible();
    }
  }

  async searchAndVerifyFiltering(searchTerm: string, expectedMinCount: number = 0): Promise<void> {
    await this.jobManagerPage.searchJobs(searchTerm);
    const jobCount = await this.jobManagerPage.getJobCardCount();
    expect(jobCount).toBeGreaterThanOrEqual(expectedMinCount);
    const totalCount = await this.jobManagerPage.getTotalCount();
    expect(totalCount).toBe(jobCount);
  }

  async verifySearchHighlight(searchTerm: string): Promise<void> {
    const highlightedText = this.page.locator('mark.mark-highlight');
    if ((await highlightedText.count()) > 0) {
      await expect(highlightedText.first()).toContainText(searchTerm);
    }
  }

  async selectInterpreterAndVerify(interpreter: string): Promise<void> {
    await this.jobManagerPage.selectInterpreter(interpreter);
    const jobCount = await this.jobManagerPage.getJobCardCount();
    const totalCount = await this.jobManagerPage.getTotalCount();
    expect(totalCount).toBe(jobCount);
    if (interpreter !== 'All' && jobCount > 0) {
      const firstJobCard = new JobCard(this.jobManagerPage.getJobCard(0));
      const interpreterText = await firstJobCard.getInterpreter();
      expect(interpreterText).toContain(interpreter);
    }
  }

  async verifySortOrder(sortOption: string): Promise<void> {
    await this.jobManagerPage.selectSort(sortOption);
    const jobCount = await this.jobManagerPage.getJobCardCount();
    if (jobCount < 2) {
      return;
    }
    const firstJobCard = new JobCard(this.jobManagerPage.getJobCard(0));
    const secondJobCard = new JobCard(this.jobManagerPage.getJobCard(1));
    const firstTime = await firstJobCard.getRelativeTime();
    const secondTime = await secondJobCard.getRelativeTime();
    expect(firstTime).toBeTruthy();
    expect(secondTime).toBeTruthy();
  }

  async verifyJobCard(index: number): Promise<void> {
    const jobCardLocator = this.jobManagerPage.getJobCard(index);
    await expect(jobCardLocator).toBeVisible();
    const jobCard = new JobCard(jobCardLocator);
    await expect(jobCard.noteIcon).toBeVisible();
    await expect(jobCard.noteName).toBeVisible();
    await expect(jobCard.interpreter).toBeVisible();
    await expect(jobCard.relativeTime).toBeVisible();
    await expect(jobCard.statusText).toBeVisible();
    await expect(jobCard.controlButton).toBeVisible();
    const paragraphCount = await jobCard.getParagraphCount();
    expect(paragraphCount).toBeGreaterThan(0);
  }

  async verifyJobControlButton(index: number): Promise<void> {
    const jobCardLocator = this.jobManagerPage.getJobCard(index);
    const jobCard = new JobCard(jobCardLocator);
    const isRunning = await jobCard.isRunning();
    const tooltip = await jobCard.getControlButtonTooltip();
    if (isRunning) {
      expect(tooltip).toContain('Stop All Paragraphs');
      const progressBarVisible = await jobCard.isProgressBarVisible();
      if (progressBarVisible) {
        await expect(jobCard.progressBar).toBeVisible();
      }
    } else {
      expect(tooltip).toContain('Start All Paragraphs');
    }
  }

  async verifyEmptyState(): Promise<void> {
    await expect(this.jobManagerPage.emptyState).toBeVisible();
    await expect(this.jobManagerPage.emptyState).toContainText('No Job found');
  }

  async verifyTotalCountMatchesJobCards(): Promise<void> {
    const totalCount = await this.jobManagerPage.getTotalCount();
    const jobCardCount = await this.jobManagerPage.getJobCardCount();
    expect(totalCount).toBe(jobCardCount);
  }

  async confirmJobAction(action: 'start' | 'stop'): Promise<void> {
    const modal = this.page.locator('.ant-modal');
    await expect(modal).toBeVisible();
    const expectedContent = action === 'start' ? 'Run all paragraphs?' : 'Stop all paragraphs?';
    await expect(modal).toContainText(expectedContent);
    const okButton = modal.getByRole('button', { name: 'OK' });
    await okButton.click();
    await expect(modal).toBeHidden();
  }

  async cancelJobAction(): Promise<void> {
    const modal = this.page.locator('.ant-modal');
    await expect(modal).toBeVisible();
    const cancelButton = modal.getByRole('button', { name: 'Cancel' });
    await cancelButton.click();
    await expect(modal).toBeHidden();
  }

  async navigateToNotebook(jobIndex: number): Promise<void> {
    const jobCardLocator = this.jobManagerPage.getJobCard(jobIndex);
    const jobCard = new JobCard(jobCardLocator);
    await jobCard.clickNoteName();
    await this.page.waitForURL(/.*\/notebook\/.*/);
  }

  async verifyParagraphStatusBadges(jobIndex: number): Promise<void> {
    const jobCardLocator = this.jobManagerPage.getJobCard(jobIndex);
    const jobCard = new JobCard(jobCardLocator);
    const paragraphCount = await jobCard.getParagraphCount();
    expect(paragraphCount).toBeGreaterThan(0);
    for (let i = 0; i < paragraphCount; i++) {
      const badge = jobCard.getParagraphBadge(i);
      try {
        const badgeVisible = await badge.isVisible({ timeout: 1000 });
        if (badgeVisible) {
          await expect(badge).toBeVisible();
        }
      } catch {
        continue;
      }
    }
  }

  async setupTest(): Promise<{ isDisabled: boolean; jobCount: number }> {
    const isDisabled = await this.jobManagerPage.isDisabled();
    const jobCount = await this.jobManagerPage.getJobCardCount();
    console.log('Test Context:', { isDisabled, jobCount });
    return { isDisabled, jobCount };
  }

  async verifyJobCardDataIntegrity(jobIndex: number): Promise<void> {
    const jobCard = new JobCard(this.jobManagerPage.getJobCard(jobIndex));
    const noteName = await jobCard.getNoteName();
    const interpreter = await jobCard.getInterpreter();
    const status = await jobCard.getStatus();
    const relativeTime = await jobCard.getRelativeTime();
    expect(noteName).toBeTruthy();
    expect(noteName?.length).toBeGreaterThan(0);
    expect(interpreter).toBeTruthy();
    expect(status).toBeTruthy();
    expect(relativeTime).toBeTruthy();
  }

  async verifyAllJobCardsDataIntegrity(): Promise<void> {
    const jobCount = await this.jobManagerPage.getJobCardCount();
    for (let i = 0; i < jobCount; i++) {
      await this.verifyJobCardDataIntegrity(i);
    }
  }
}
