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

import { expect, test } from '@playwright/test';
import { JobCard } from '../../models/job-card';
import { JobManagerPage } from '../../models/job-manager-page';
import { JobManagerUtil } from '../../models/job-manager-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, PAGES } from '../../utils';

test.describe('Job Manager - Job Cards', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB);

  test.beforeEach(async ({ page }) => {
    await performLoginIfRequired(page);
    const jobManagerPage = new JobManagerPage(page);
    await jobManagerPage.navigate();
  });

  test('should display job card with all required elements', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyJobCard(0);
  });

  test('should display correct note icon based on note type', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    await expect(jobCard.noteIcon).toBeVisible();

    const iconType = await jobCard.getNoteIconType();
    const validIconTypes = ['file', 'close-circle', 'file-unknown'];
    expect(validIconTypes).toContain(iconType);
  });

  test('should display interpreter name or unset message', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const interpreter = await jobCard.getInterpreter();

    expect(interpreter).toBeTruthy();
    expect(interpreter?.length).toBeGreaterThan(0);
  });

  test('should display relative time for last run', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    await expect(jobCard.relativeTime).toBeVisible();

    const timeText = await jobCard.getRelativeTime();
    expect(timeText).toBeTruthy();
  });

  test('should display job status', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const status = await jobCard.getStatus();

    const validStatuses = ['READY', 'RUNNING', 'FINISHED', 'ERROR', 'ABORT', 'PENDING'];
    expect(validStatuses).toContain(status);
  });

  test('should display control button with correct tooltip', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyJobControlButton(0);
  });

  test('should display progress bar for running jobs', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const isRunning = await jobCard.isRunning();

    if (isRunning) {
      await expect(jobCard.progressBar).toBeVisible();
      await expect(jobCard.progressPercentage).toBeVisible();

      const progress = await jobCard.getProgress();
      expect(progress).toBeTruthy();
    }
  });

  test('should display paragraph status badges', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyParagraphStatusBadges(0);
  });

  test('should navigate to notebook when clicking note name', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    await jobCard.clickNoteName();

    await page.waitForURL(/.*\/notebook\/.*/);
  });

  test('should show confirmation modal when starting job', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const isRunning = await jobCard.isRunning();

    if (!isRunning) {
      await jobCard.clickControlButton();

      const modal = page.locator('.ant-modal');
      await expect(modal).toBeVisible();
      await expect(modal).toContainText('Run all paragraphs?');

      const cancelButton = modal.getByRole('button', { name: 'Cancel' });
      await cancelButton.click();
    }
  });

  test('should show confirmation modal when stopping job', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const isRunning = await jobCard.isRunning();

    if (isRunning) {
      await jobCard.clickControlButton();

      const modal = page.locator('.ant-modal');
      await expect(modal).toBeVisible();
      await expect(modal).toContainText('Stop all paragraphs?');

      const cancelButton = modal.getByRole('button', { name: 'Cancel' });
      await cancelButton.click();
    }
  });

  test('should navigate to specific paragraph when clicking paragraph badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const jobCard = new JobCard(jobManagerPage.getJobCard(0));
    const paragraphCount = await jobCard.getParagraphCount();

    if (paragraphCount > 0) {
      const badge = jobCard.getParagraphBadge(0);
      await badge.click();

      await page.waitForURL(/.*\/notebook\/.*/);
      expect(page.url()).toContain('paragraph=');
    }
  });

  test('should verify job card data integrity', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyJobCardDataIntegrity(0);
  });

  test('should verify all job cards have valid data', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyAllJobCardsDataIntegrity();
  });
});
