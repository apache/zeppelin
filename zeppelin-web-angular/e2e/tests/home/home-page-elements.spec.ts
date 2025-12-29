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

test.describe('Home Page - Core Elements', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);

  let homePage: HomePage;

  test.beforeEach(async ({ page }) => {
    homePage = new HomePage(page);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);
  });

  test.describe('Welcome Section', () => {
    test('should display welcome section with correct content', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When the page loads', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see the welcome section with correct content', async () => {
        await expect(homePage.welcomeSection).toBeVisible();
        await expect(homePage.welcomeTitle).toBeVisible();
        const headingText = await homePage.getWelcomeHeadingText();
        expect(headingText.trim()).toBe('Welcome to Zeppelin!');
        await expect(homePage.welcomeDescription).toBeVisible();
        const welcomeText = await homePage.welcomeDescription.textContent();
        expect(welcomeText).toContain('web-based notebook');
        expect(welcomeText).toContain('interactive data analytics');
      });
    });

    test('should have proper welcome message structure', async () => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I examine the welcome section', async () => {
        await expect(homePage.welcomeSection).toBeVisible();
      });

      await test.step('Then I should see the welcome heading', async () => {
        await expect(homePage.welcomeTitle).toBeVisible();
        const headingText = await homePage.getWelcomeHeadingText();
        expect(headingText.trim()).toBe('Welcome to Zeppelin!');
      });

      await test.step('And I should see the welcome description', async () => {
        await expect(homePage.welcomeDescription).toBeVisible();
        const descriptionText = await homePage.getWelcomeDescriptionText();
        expect(descriptionText).toContain('web-based notebook');
        expect(descriptionText).toContain('interactive data analytics');
      });
    });
  });

  test.describe('Notebook Section', () => {
    test('should display notebook section with all components', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I look for the notebook section', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see all notebook section components', async () => {
        await expect(homePage.notebookSection).toBeVisible();
        await expect(homePage.notebookHeading).toBeVisible();
        await expect(homePage.refreshNoteButton).toBeVisible();
        await page.waitForSelector('zeppelin-node-list', { timeout: 10000 });
        await expect(homePage.zeppelinNodeList).toBeVisible();
      });
    });

    test('should have functional refresh notes button', async () => {
      await test.step('Given I am on the home page with notebook section visible', async () => {
        await homePage.navigateToHome();
        await expect(homePage.refreshNoteButton).toBeVisible();
      });

      await test.step('When I click the refresh notes button', async () => {
        await homePage.clickRefreshNotes();
      });

      await test.step('Then the notebook list should still be visible', async () => {
        await homePage.waitForRefreshToComplete();
        await expect(homePage.zeppelinNodeList).toBeVisible();
        const isStillVisible = await homePage.zeppelinNodeList.isVisible();
        expect(isStillVisible).toBe(true);
      });
    });

    test('should display notebook list component', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I look for the notebook list', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see the notebook list component', async () => {
        await expect(homePage.zeppelinNodeList).toBeVisible();
        const isVisible = await homePage.isNotebookListVisible();
        expect(isVisible).toBe(true);
      });
    });
  });

  test.describe('Help Section', () => {
    test('should display help section with documentation link', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I look for the help section', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see the help section', async () => {
        await expect(homePage.helpSection).toBeVisible();
        await expect(homePage.helpHeading).toBeVisible();
      });

      await test.step('And I should see the documentation link', async () => {
        await expect(homePage.externalLinks.documentation).toBeVisible();
      });
    });
  });

  test.describe('Community Section', () => {
    test('should display community section with all links', async ({ page }) => {
      await test.step('Given I am on the home page', async () => {
        await homePage.navigateToHome();
      });

      await test.step('When I look for the community section', async () => {
        await waitForZeppelinReady(page);
      });

      await test.step('Then I should see the community section', async () => {
        await expect(homePage.communitySection).toBeVisible();
        await expect(homePage.communityHeading).toBeVisible();
      });

      await test.step('And I should see all community links', async () => {
        await expect(homePage.externalLinks.documentation).toBeVisible();
        await expect(homePage.externalLinks.mailingList).toBeVisible();
        await expect(homePage.externalLinks.issuesTracking).toBeVisible();
        await expect(homePage.externalLinks.github).toBeVisible();
      });
    });
  });
});
