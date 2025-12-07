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

import { test, Page, TestInfo } from '@playwright/test';
import { LoginTestUtil } from './models/login-page.util';
import { NotebookUtil } from './models/notebook.util';
import { E2E_TEST_FOLDER } from './models/base-page';

export const PAGES = {
  // Main App
  APP: 'src/app/app.component',

  // Core
  CORE: {
    DESTROY_HOOK: 'src/app/core/destroy-hook/destroy-hook.component'
  },

  // Pages
  PAGES: {
    LOGIN: 'src/app/pages/login/login.component'
  },

  // Pages - Workspace
  WORKSPACE: {
    MAIN: 'src/app/pages/workspace/workspace.component',
    CONFIGURATION: 'src/app/pages/workspace/configuration/configuration.component',
    CREDENTIAL: 'src/app/pages/workspace/credential/credential.component',
    HOME: 'src/app/pages/workspace/home/home.component',
    INTERPRETER: 'src/app/pages/workspace/interpreter/interpreter.component',
    INTERPRETER_CREATE_REPO:
      'src/app/pages/workspace/interpreter/create-repository-modal/create-repository-modal.component',
    INTERPRETER_ITEM: 'src/app/pages/workspace/interpreter/item/item.component',
    JOB_MANAGER: 'src/app/pages/workspace/job-manager/job-manager.component',
    JOB_STATUS: 'src/app/pages/workspace/job-manager/job-status/job-status.component',
    JOB: 'src/app/pages/workspace/job-manager/job/job.component',
    NOTEBOOK_REPOS: 'src/app/pages/workspace/notebook-repos/notebook-repos.component',
    NOTEBOOK_REPOS_ITEM: 'src/app/pages/workspace/notebook-repos/item/item.component',
    NOTEBOOK_SEARCH: 'src/app/pages/workspace/notebook-search/notebook-search.component',
    NOTEBOOK_SEARCH_RESULT: 'src/app/pages/workspace/notebook-search/result-item/result-item.component',
    NOTEBOOK: 'src/app/pages/workspace/notebook/notebook.component',
    NOTEBOOK_ACTION_BAR: 'src/app/pages/workspace/notebook/action-bar/action-bar.component',
    NOTEBOOK_ADD_PARAGRAPH: 'src/app/pages/workspace/notebook/add-paragraph/add-paragraph.component',
    NOTEBOOK_INTERPRETER_BINDING: 'src/app/pages/workspace/notebook/interpreter-binding/interpreter-binding.component',
    NOTEBOOK_NOTE_FORM: 'src/app/pages/workspace/notebook/note-form-block/note-form-block.component',
    NOTEBOOK_PERMISSIONS: 'src/app/pages/workspace/notebook/permissions/permissions.component',
    NOTEBOOK_REVISIONS: 'src/app/pages/workspace/notebook/revisions-comparator/revisions-comparator.component',
    NOTEBOOK_ELASTIC_INPUT: 'src/app/pages/workspace/notebook/share/elastic-input/elastic-input.component',
    NOTEBOOK_SIDEBAR: 'src/app/pages/workspace/notebook/sidebar/sidebar.component',
    NOTEBOOK_PARAGRAPH: 'src/app/pages/workspace/notebook/paragraph/paragraph.component',
    NOTEBOOK_PARAGRAPH_CODE_EDITOR: 'src/app/pages/workspace/notebook/paragraph/code-editor/code-editor.component',
    NOTEBOOK_PARAGRAPH_CONTROL: 'src/app/pages/workspace/notebook/paragraph/control/control.component',
    NOTEBOOK_PARAGRAPH_FOOTER: 'src/app/pages/workspace/notebook/paragraph/footer/footer.component',
    NOTEBOOK_PARAGRAPH_PROGRESS: 'src/app/pages/workspace/notebook/paragraph/progress/progress.component',
    PUBLISHED_PARAGRAPH: 'src/app/pages/workspace/published/paragraph/paragraph.component',
    SHARE_DYNAMIC_FORMS: 'src/app/pages/workspace/share/dynamic-forms/dynamic-forms.component',
    SHARE_RESULT: 'src/app/pages/workspace/share/result/result.component'
  },

  // Share
  SHARE: {
    ABOUT_ZEPPELIN: 'src/app/share/about-zeppelin/about-zeppelin.component',
    CODE_EDITOR: 'src/app/share/code-editor/code-editor.component',
    FOLDER_RENAME: 'src/app/share/folder-rename/folder-rename.component',
    HEADER: 'src/app/share/header/header.component',
    NODE_LIST: 'src/app/share/node-list/node-list.component',
    NOTE_CREATE: 'src/app/share/note-create/note-create.component',
    NOTE_IMPORT: 'src/app/share/note-import/note-import.component',
    NOTE_RENAME: 'src/app/share/note-rename/note-rename.component',
    NOTE_TOC: 'src/app/share/note-toc/note-toc.component',
    PAGE_HEADER: 'src/app/share/page-header/page-header.component',
    RESIZE_HANDLE: 'src/app/share/resize-handle/resize-handle.component',
    SHORTCUT: 'src/app/share/shortcut/shortcut.component',
    SPIN: 'src/app/share/spin/spin.component',
    THEME_TOGGLE: 'src/app/share/theme-toggle/theme-toggle.component'
  },

  // Visualizations
  VISUALIZATIONS: {
    AREA_CHART: 'src/app/visualizations/area-chart/area-chart-visualization.component',
    BAR_CHART: 'src/app/visualizations/bar-chart/bar-chart-visualization.component',
    LINE_CHART: 'src/app/visualizations/line-chart/line-chart-visualization.component',
    PIE_CHART: 'src/app/visualizations/pie-chart/pie-chart-visualization.component',
    SCATTER_CHART: 'src/app/visualizations/scatter-chart/scatter-chart-visualization.component',
    TABLE: 'src/app/visualizations/table/table-visualization.component',
    COMMON: {
      PIVOT_SETTING: 'src/app/visualizations/common/pivot-setting/pivot-setting.component',
      SCATTER_SETTING: 'src/app/visualizations/common/scatter-setting/scatter-setting.component',
      X_AXIS_SETTING: 'src/app/visualizations/common/x-axis-setting/x-axis-setting.component'
    }
  }
} as const;

export const NOTEBOOK_PATTERNS = {
  URL_REGEX: /\/notebook\/[^\/\?]+/,
  URL_EXTRACT_NOTEBOOK_ID_REGEX: /\/notebook\/([^\/\?]+)/
} as const;

export const addPageAnnotation = (pageName: string, testInfo: TestInfo) => {
  testInfo.annotations.push({
    type: 'page',
    description: pageName
  });
};

export const addPageAnnotationBeforeEach = (pageName: string) => {
  test.beforeEach(async ({}, testInfo) => {
    addPageAnnotation(pageName, testInfo);
  });
};

interface PageStructureType {
  [key: string]: string | PageStructureType;
}

export const flattenPageComponents = (pages: PageStructureType): string[] => {
  const result: string[] = [];

  const flatten = (obj: PageStructureType) => {
    for (const value of Object.values(obj)) {
      if (typeof value === 'string') {
        result.push(value);
      } else if (typeof value === 'object' && value !== null) {
        flatten(value);
      }
    }
  };

  flatten(pages);
  return result.sort();
};

export const getCoverageTransformPaths = (): string[] => flattenPageComponents(PAGES);

export const waitForUrlNotContaining = async (page: Page, fragment: string) => {
  await page.waitForURL(url => !url.toString().includes(fragment));
};

export const getCurrentPath = (page: Page): string => {
  const url = new URL(page.url());
  return url.hash || url.pathname;
};

export const getBasicPageMetadata = async (
  page: Page
): Promise<{
  title: string;
  path: string;
}> => ({
  title: await page.title(),
  path: getCurrentPath(page)
});

export const performLoginIfRequired = async (page: Page): Promise<boolean> => {
  const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
  if (!isShiroEnabled) {
    return false;
  }

  const credentials = await LoginTestUtil.getTestCredentials();
  const validUsers = Object.values(credentials).filter(
    cred => cred.username && cred.password && cred.username !== 'INVALID_USER' && cred.username !== 'EMPTY_CREDENTIALS'
  );

  if (validUsers.length === 0) {
    return false;
  }

  const testUser = validUsers[0];

  const isLoginVisible = await page.locator('zeppelin-login').isVisible();
  if (isLoginVisible) {
    const userNameInput = page.getByRole('textbox', { name: 'User Name' });
    const passwordInput = page.getByRole('textbox', { name: 'Password' });
    const loginButton = page.getByRole('button', { name: 'Login' });

    await userNameInput.fill(testUser.username);
    await passwordInput.fill(testUser.password);
    await loginButton.click();

    // for webkit
    await page.waitForTimeout(200);
    await page.evaluate(() => {
      if (window.location.hash.includes('login')) {
        window.location.hash = '#/';
      }
    });

    try {
      await page.waitForSelector('zeppelin-login', { state: 'hidden', timeout: 30000 });
      await page.waitForSelector('zeppelin-page-header >> text=Home', { timeout: 30000 });
      await page.waitForSelector('text=Welcome to Zeppelin!', { timeout: 30000 });
      await page.waitForSelector('zeppelin-node-list', { timeout: 30000 });
      return true;
    } catch {
      return false;
    }
  }

  return false;
};

export const waitForZeppelinReady = async (page: Page): Promise<void> => {
  try {
    // Enhanced wait for network idle with longer timeout for CI environments
    await page.waitForLoadState('domcontentloaded', { timeout: 45000 });

    // Check if we're on login page and authentication is required
    const isOnLoginPage = page.url().includes('#/login');
    if (isOnLoginPage) {
      console.log('On login page - checking if authentication is enabled');

      // If we're on login dlpage, this is expected when authentication is required
      // Just wait for login elements to be ready instead of waiting for app content
      await page.waitForFunction(
        () => {
          const hasAngular = document.querySelector('[ng-version]') !== null;
          const hasLoginElements =
            document.querySelector('zeppelin-login') !== null ||
            document.querySelector('input[placeholder*="User"], input[placeholder*="user"], input[type="text"]') !==
              null;
          return hasAngular && hasLoginElements;
        },
        { timeout: 30000 }
      );
      console.log('Login page is ready');
      return;
    }

    // Wait for Angular and Zeppelin to be ready with more robust checks
    await page.waitForFunction(
      () => {
        // Check for Angular framework
        const hasAngular = document.querySelector('[ng-version]') !== null;

        // Check for Zeppelin-specific content
        const hasZeppelinContent =
          document.body.textContent?.includes('Zeppelin') ||
          document.body.textContent?.includes('Notebook') ||
          document.body.textContent?.includes('Welcome');

        // Check for Zeppelin root element
        const hasZeppelinRoot = document.querySelector('zeppelin-root') !== null;

        // Check for basic UI elements that indicate the app is ready
        const hasBasicUI =
          document.querySelector('button, input, .ant-btn') !== null ||
          document.querySelector('[class*="zeppelin"]') !== null;

        return hasAngular && (hasZeppelinContent || hasZeppelinRoot || hasBasicUI);
      },
      { timeout: 90000 }
    );

    // Additional stability check - wait for DOM to be stable
    await page.waitForLoadState('domcontentloaded');
  } catch (error) {
    throw new Error(`Zeppelin loading failed: ${String(error)}`);
  }
};

export const waitForNotebookLinks = async (page: Page, timeout: number = 30000) => {
  const locator = page.locator('a[href*="#/notebook/"]');

  // If there are no notebook links on the page, there's no reason to wait
  const count = await locator.count();
  if (count === 0) {
    return;
  }

  await locator.first().waitFor({ state: 'visible', timeout });
};

export const navigateToNotebookWithFallback = async (
  page: Page,
  noteId: string,
  notebookName?: string
): Promise<void> => {
  let navigationSuccessful = false;

  try {
    // Strategy 1: Direct navigation
    await page.goto(`/#/notebook/${noteId}`, { waitUntil: 'networkidle', timeout: 30000 });
    navigationSuccessful = true;
  } catch (error) {
    console.log('Direct navigation failed, trying fallback strategies...');

    // Strategy 2: Wait for loading completion and check URL
    await page.waitForFunction(
      () => {
        const loadingText = document.body.textContent || '';
        return !loadingText.includes('Getting Ticket Data');
      },
      { timeout: 15000 }
    );

    const currentUrl = page.url();
    if (currentUrl.includes('/notebook/')) {
      navigationSuccessful = true;
    }

    // Strategy 3: Navigate through home page if notebook name is provided
    if (!navigationSuccessful && notebookName) {
      await page.goto('/#/');
      await page.waitForLoadState('networkidle', { timeout: 15000 });
      await page.waitForSelector('zeppelin-node-list', { timeout: 15000 });

      // The link text in the UI is the base name of the note, not the full path.
      const baseName = notebookName.split('/').pop();
      const notebookLink = page.locator(`a[href*="/notebook/"]`).filter({ hasText: baseName! });
      // Use the click action's built-in wait.
      await notebookLink.click({ timeout: 10000 });

      await page.waitForURL(NOTEBOOK_PATTERNS.URL_REGEX, { timeout: 20000 });
      navigationSuccessful = true;
    }
  }

  if (!navigationSuccessful) {
    throw new Error(`Failed to navigate to notebook ${noteId}`);
  }

  // Wait for notebook to be ready
  await waitForZeppelinReady(page);
};

const extractNoteIdFromUrl = async (page: Page): Promise<string | null> => {
  const url = page.url();
  const match = url.match(NOTEBOOK_PATTERNS.URL_EXTRACT_NOTEBOOK_ID_REGEX);
  return match ? match[1] : null;
};

const waitForNotebookNavigation = async (page: Page): Promise<string | null> => {
  await page.waitForURL(NOTEBOOK_PATTERNS.URL_REGEX, { timeout: 30000 });
  return await extractNoteIdFromUrl(page);
};

const navigateViaHomePageFallback = async (page: Page, baseNotebookName: string): Promise<string> => {
  await page.goto('/#/');
  await page.waitForLoadState('networkidle', { timeout: 15000 });
  await page.waitForSelector('zeppelin-node-list', { timeout: 15000 });

  await page.waitForFunction(() => document.querySelectorAll('a[href*="/notebook/"]').length > 0, {
    timeout: 15000
  });
  await page.waitForLoadState('domcontentloaded', { timeout: 15000 });

  const notebookLink = page.locator(`a[href*="/notebook/"]`).filter({ hasText: baseNotebookName });

  const browserName = page.context().browser()?.browserType().name();
  if (browserName === 'firefox') {
    await page.waitForSelector(`a[href*="/notebook/"]:has-text("${baseNotebookName}")`, {
      state: 'visible',
      timeout: 90000
    });
  } else {
    await notebookLink.waitFor({ state: 'visible', timeout: 60000 });
  }

  await notebookLink.click({ timeout: 15000 });
  await page.waitForURL(NOTEBOOK_PATTERNS.URL_REGEX, { timeout: 20000 });

  const noteId = await extractNoteIdFromUrl(page);
  if (!noteId) {
    throw new Error('Failed to extract notebook ID after home page navigation');
  }

  return noteId;
};

const extractFirstParagraphId = async (page: Page): Promise<string> => {
  await page.locator('zeppelin-notebook-paragraph').first().waitFor({ state: 'visible', timeout: 10000 });

  const paragraphContainer = page.locator('zeppelin-notebook-paragraph').first();
  const dropdownTrigger = paragraphContainer.locator('a[nz-dropdown]');
  await dropdownTrigger.click();

  const paragraphLink = page.locator('li.paragraph-id a').first();
  await paragraphLink.waitFor({ state: 'attached', timeout: 15000 });

  const paragraphId = await paragraphLink.textContent();
  if (!paragraphId || !paragraphId.startsWith('paragraph_')) {
    throw new Error(`Invalid paragraph ID found: ${paragraphId}`);
  }

  return paragraphId;
};

export const createTestNotebook = async (
  page: Page,
  folderPath?: string
): Promise<{ noteId: string; paragraphId: string }> => {
  const notebookUtil = new NotebookUtil(page);
  const baseNotebookName = `/TestNotebook_${Date.now()}`;
  const notebookName = folderPath
    ? `${E2E_TEST_FOLDER}/${folderPath}/${baseNotebookName}`
    : `${E2E_TEST_FOLDER}/${baseNotebookName}`;

  try {
    // Create notebook
    await notebookUtil.createNotebook(notebookName);

    let noteId: string | null = null;

    // Try direct navigation first
    noteId = await waitForNotebookNavigation(page);

    if (!noteId) {
      console.log('Direct navigation failed, trying fallback strategies...');

      // Check if we're already on a notebook page
      noteId = await extractNoteIdFromUrl(page);

      if (noteId) {
        // Use existing fallback navigation
        await navigateToNotebookWithFallback(page, noteId, notebookName);
      } else {
        // Navigate via home page as last resort
        noteId = await navigateViaHomePageFallback(page, baseNotebookName);
      }
    }

    if (!noteId) {
      throw new Error(`Failed to extract notebook ID from URL: ${page.url()}`);
    }

    // Extract paragraph ID
    const paragraphId = await extractFirstParagraphId(page);

    // Navigate back to home
    await page.goto('/#/');
    await waitForZeppelinReady(page);

    return { noteId, paragraphId };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    const currentUrl = page.url();
    throw new Error(`Failed to create test notebook: ${errorMessage}. Current URL: ${currentUrl}`);
  }
};
