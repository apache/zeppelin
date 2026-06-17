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

test.describe('Job Manager - Filter Controls', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.JOB_MANAGER);

  test.beforeEach(async ({ page }) => {
    await performLoginIfRequired(page);
    const jobManagerPage = new JobManagerPage(page);
    await jobManagerPage.navigate();
  });

  test('should filter jobs by search term', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.searchAndVerifyFiltering('note', 0);
  });

  test('should handle search with special characters', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    await jobManagerPage.searchJobs('*.*');
    const totalCount = await jobManagerPage.getTotalCount();
    expect(totalCount).toBeGreaterThanOrEqual(0);
  });

  test('should perform case-insensitive search', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerPage.searchJobs('NOTE');
    const uppercaseCount = await jobManagerPage.getTotalCount();

    await jobManagerPage.searchJobs('note');
    const lowercaseCount = await jobManagerPage.getTotalCount();

    expect(uppercaseCount).toBe(lowercaseCount);
  });

  test('should highlight search terms in job names', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerPage.searchJobs('note');
    const filteredCount = await jobManagerPage.getJobCardCount();
    if (filteredCount > 0) {
      await jobManagerUtil.verifySearchHighlight('note');
    }
  });

  test('should filter jobs by interpreter', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.selectInterpreterAndVerify('All');
  });

  test('should sort jobs by Recently Update', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount < 2) {
      test.skip(true, 'Job Manager is disabled or insufficient jobs');
    }

    await jobManagerUtil.verifySortOrder('Recently Update');
  });

  test('should sort jobs by Oldest Updated', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount < 2) {
      test.skip(true, 'Job Manager is disabled or insufficient jobs');
    }

    await jobManagerUtil.verifySortOrder('Oldest Updated');
  });

  test('should update total counter when filtering', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerUtil.verifyTotalCountMatchesJobCards();

    await jobManagerPage.searchJobs('nonexistent_job_xyz_123456');
    await jobManagerUtil.verifyTotalCountMatchesJobCards();
  });

  test('should display empty state when no jobs match filter', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled } = await jobManagerUtil.setupTest();
    if (isDisabled) {
      test.skip(true, 'Job Manager is disabled');
    }

    await jobManagerPage.searchJobs('nonexistent_job_xyz_123456');
    const isEmpty = await jobManagerPage.isEmpty();
    if (isEmpty) {
      await jobManagerUtil.verifyEmptyState();
    }
  });

  test('should clear filters correctly', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    const initialCount = jobCount;

    await jobManagerPage.searchJobs('note');
    const filteredCount = await jobManagerPage.getJobCardCount();

    await jobManagerPage.clearFilters();
    const clearedCount = await jobManagerPage.getJobCardCount();

    expect(clearedCount).toBe(initialCount);
    expect(clearedCount).toBeGreaterThanOrEqual(filteredCount);
  });

  test('should maintain filter state during page interactions', async ({ page }) => {
    const jobManagerPage = new JobManagerPage(page);
    const jobManagerUtil = new JobManagerUtil(page);

    const { isDisabled, jobCount } = await jobManagerUtil.setupTest();
    if (isDisabled || jobCount === 0) {
      test.skip(true, 'Job Manager is disabled or no jobs available');
    }

    await jobManagerPage.searchJobs('note');
    const searchCount = await jobManagerPage.getJobCardCount();

    await jobManagerPage.selectInterpreter('All');
    const combinedCount = await jobManagerPage.getJobCardCount();

    expect(combinedCount).toBeLessThanOrEqual(searchCount);
  });
});
