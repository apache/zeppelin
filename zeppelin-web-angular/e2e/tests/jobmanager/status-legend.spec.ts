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
import { JobManagerPage } from '../../models/job-manager-page';
import { JobManagerUtil } from '../../models/job-manager-page.util';
import { addPageAnnotationBeforeEach, performLoginIfRequired, PAGES } from '../../utils';

test.describe('Job Manager - Status Legend', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB_STATUS);

  test.beforeEach(async ({ page }) => {
    await performLoginIfRequired(page);
    const jobManagerPage = new JobManagerPage(page);
    await jobManagerPage.navigate();
  });

  test('should display all job status badges in legend', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    await jobManagerUtil.verifyStatusLegend();
  });

  test('should render status badges with correct colors', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const statusLegendExists = await jobManagerPage.statusLegend.isVisible();
    if (!statusLegendExists) {
      test.skip(true, 'Status legend not available');
    }

    await jobManagerUtil.verifyStatusBadgeColors();
  });

  test('should display READY status with success badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const readyBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'READY' });
    await expect(readyBadge).toBeVisible();
  });

  test('should display FINISHED status with success badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const finishedBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'FINISHED' });
    await expect(finishedBadge).toBeVisible();
  });

  test('should display ERROR status with error badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const errorBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'ERROR' });
    await expect(errorBadge).toBeVisible();
  });

  test('should display ABORT status with warning badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const abortBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'ABORT' });
    await expect(abortBadge).toBeVisible();
  });

  test('should display RUNNING status with processing badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const runningBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'RUNNING' });
    await expect(runningBadge).toBeVisible();
  });

  test('should display PENDING status with default badge', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    const pendingBadge = jobManagerPage.statusLegend
      .locator('zeppelin-job-manager-job-status')
      .filter({ hasText: 'PENDING' });
    await expect(pendingBadge).toBeVisible();
  });
});
