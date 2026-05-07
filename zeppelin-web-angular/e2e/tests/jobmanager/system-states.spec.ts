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

test.describe('Job Manager - System States', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB_MANAGER);

  test.beforeEach(async ({ page }) => {
    await performLoginIfRequired(page);
  });

  test('should display page header correctly', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    await jobManagerPage.navigate();
    await jobManagerUtil.verifyPageHeader();
  });

  test('should handle disabled state when Job Manager is disabled', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    await jobManagerPage.navigate();

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      await jobManagerUtil.verifyDisabledState();
      const message = await jobManagerPage.getDisabledMessage();
      expect(message).toContain('Job Manager is disabled in the current configuration');
    }
  });

  test('should display loading state during initialization', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);

    await page.goto('/#/jobmanager');

    const skeletonExists = await jobManagerPage.loadingSkeleton.isVisible().catch(() => false);
    if (skeletonExists) {
      await expect(jobManagerPage.loadingSkeleton).toHaveAttribute('ng-reflect-nz-active', 'true');
    }

    await jobManagerPage.waitForPageLoad();
  });

  test('should display success state with job list when enabled', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    await jobManagerPage.navigate();

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (!isDisabled) {
      await expect(jobManagerPage.pageHeader).toBeVisible();
      await expect(jobManagerPage.searchInput).toBeVisible();
      await expect(jobManagerPage.interpreterSelect).toBeVisible();
      await expect(jobManagerPage.sortSelect).toBeVisible();
      await expect(jobManagerPage.totalCounter).toBeVisible();
      await expect(jobManagerPage.statusLegend).toBeVisible();
    } else {
      test.skip(isDisabled, 'Job Manager is disabled');
    }
  });

  test('should handle page refresh correctly', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    await jobManagerPage.navigate();
    await jobManagerUtil.setupTest();

    await page.reload();
    await jobManagerPage.waitForPageLoad();

    await expect(jobManagerPage.pageHeader).toBeVisible();
  });

  test('should handle navigation from other pages', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);

    await page.goto('/#/');
    await page.waitForLoadState('networkidle');

    await jobManagerPage.navigate();
    await expect(jobManagerPage.pageHeader).toBeVisible();
  });
});
