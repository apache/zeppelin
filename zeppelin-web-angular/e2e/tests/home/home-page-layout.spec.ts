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
import { HomePage } from '../../models/home-page';
import { addPageAnnotationBeforeEach, performLoginIfRequired, waitForZeppelinReady, PAGES } from '../../utils';

test.describe('Home Page - Layout and Grid', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

  let homePage: HomePage;

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Responsive Grid Layout', () => {
    test('should display responsive grid structure', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When the page loads', async () => {
        await waitForZeppelinReady(page);
      });
    });

    test('should have proper column distribution', async () => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the grid columns', async () => {
        await expect(homePage.moreInfoGrid).toBeVisible();
      });

      await test.step('Then I should see the notebook column with proper sizing', async () => {
        await expect(homePage.notebookColumn).toBeVisible();
        // Check that the column contains notebook content
        const notebookHeading = homePage.notebookColumn.locator('h3');
        await expect(notebookHeading).toBeVisible();
        const headingText = await notebookHeading.textContent();
        expect(headingText).toContain('Notebook');
      });

      await test.step('And I should see the help/community column with proper sizing', async () => {
        await expect(homePage.helpCommunityColumn).toBeVisible();
        // Check that the column contains help and community content
        const helpHeading = homePage.helpCommunityColumn.locator('h3').first();
        await expect(helpHeading).toBeVisible();
        const helpText = await helpHeading.textContent();
        expect(helpText).toContain('Help');
      });
    });

    test('should maintain layout structure across different viewport sizes', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I resize to tablet view', async () => {
        await page.setViewportSize({ width: 768, height: 1024 });
      });

      await test.step('Then the grid should still be visible and functional', async () => {
        await expect(homePage.moreInfoGrid).toBeVisible();
        await expect(homePage.notebookColumn).toBeVisible();
        await expect(homePage.helpCommunityColumn).toBeVisible();
      });

      await test.step('When I resize to mobile view', async () => {
        await page.setViewportSize({ width: 375, height: 667 });
      });

      await test.step('Then the grid should adapt to mobile layout', async () => {
        await expect(homePage.moreInfoGrid).toBeVisible();
        await expect(homePage.notebookColumn).toBeVisible();
        await expect(homePage.helpCommunityColumn).toBeVisible();

        // Verify content is still accessible in mobile view
        const notebookHeading = homePage.notebookColumn.locator('h3');
        const helpHeading = homePage.helpCommunityColumn.locator('h3').first();
        await expect(notebookHeading).toBeVisible();
        await expect(helpHeading).toBeVisible();
      });
    });
  });

  test.describe('Content Organization', () => {
    test('should organize content in logical sections', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the content organization', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see the welcome section at the top', async () => {
        await expect(homePage.welcomeSection).toBeVisible();
      });

      await test.step('And I should see the notebook section in the left column', async () => {
        const notebookInColumn = homePage.notebookColumn.locator('h3');
        await expect(notebookInColumn).toBeVisible();
        const text = await notebookInColumn.textContent();
        expect(text).toContain('Notebook');
      });

      await test.step('And I should see help and community sections in the right column', async () => {
        const helpHeading = homePage.helpCommunityColumn.locator('h3').filter({ hasText: 'Help' });
        const communityHeading = homePage.helpCommunityColumn.locator('h3').filter({ hasText: 'Community' });
        await expect(helpHeading).toBeVisible();
        await expect(communityHeading).toBeVisible();
      });
    });
  });
});
