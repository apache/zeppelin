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

import { test, expect, Page, TestInfo } from '@playwright/test';
import { LoginTestUtil } from './models/login-page.util';
import { E2E_TEST_FOLDER } from './models/base-page';
import { LoginPage } from './models/login-page';

export const NOTEBOOK_PATTERNS = {
  URL_REGEX: /\/notebook\/[^\/\?]+/,
  LINK_SELECTOR: 'a[href*="/notebook/"]'
} as const;

// Coverage denominator. Structural/shared components
// (lifecycle hooks, spin, resize-handle, page-header) are intentionally omitted;
// they have no page-level behavior and are exercised transitively.
export const PAGES = {
  // Main App
  APP: 'src/app/app.component',

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
    SHORTCUT: 'src/app/share/shortcut/shortcut.component',
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
  await page.waitForLoadState('domcontentloaded', { timeout: 10000 });
  await page.waitForURL(url => !url.toString().includes(fragment), { timeout: 15000 });
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

interface WaitForZeppelinReadyOptions {
  allowLoginPage?: boolean;
}

const isLoginPageVisible = async (page: Page): Promise<boolean> =>
  page
    .locator('zeppelin-login')
    .isVisible()
    .catch(() => false);

const waitForLoginPageReady = async (page: Page): Promise<void> => {
  await page.waitForFunction(
    // JUSTIFIED: multi-condition AND — Angular presence + login element OR across three selectors; can't express as single locator wait
    () => {
      const hasAngular = document.querySelector('[ng-version]') !== null;
      const hasLoginElements =
        document.querySelector('zeppelin-login') !== null ||
        document.querySelector('input[placeholder*="User"], input[placeholder*="user"], input[type="text"]') !== null;
      return hasAngular && hasLoginElements;
    },
    { timeout: 30000 }
  );
};

const waitForWorkspaceOrLogin = async (page: Page): Promise<'workspace' | 'login' | undefined> =>
  new Promise(resolve => {
    let pending = 3;
    let resolved = false;

    const finish = (state?: 'workspace' | 'login') => {
      if (resolved) {
        return;
      }
      if (state) {
        resolved = true;
        resolve(state);
        return;
      }
      pending -= 1;
      if (pending === 0) {
        resolved = true;
        resolve(undefined);
      }
    };

    page
      .locator('zeppelin-workspace')
      .waitFor({ state: 'attached', timeout: 45000 })
      .then(() => finish('workspace'))
      .catch(() => finish());
    page
      .locator('zeppelin-login')
      .waitFor({ state: 'visible', timeout: 45000 })
      .then(() => finish('login'))
      .catch(() => finish());
    page
      .waitForURL(url => url.toString().includes('#/login'), { timeout: 45000 })
      .then(() => finish('login'))
      .catch(() => finish());
  });

const handleLoginPageIfNeeded = async (page: Page, options: WaitForZeppelinReadyOptions): Promise<boolean> => {
  const isOnLoginPage = page.url().includes('#/login') || (await isLoginPageVisible(page));
  if (!isOnLoginPage) {
    return false;
  }

  await waitForLoginPageReady(page);

  if (options.allowLoginPage) {
    return true;
  }

  if (await LoginTestUtil.isShiroEnabled()) {
    const loggedIn = await performLoginIfRequired(page);
    if (loggedIn) {
      return true;
    }

    throw new Error('Authentication is required, but the test page remained on the login screen');
  }

  return true;
};

export const performLoginIfRequired = async (page: Page): Promise<boolean> => {
  const isShiroEnabled = await LoginTestUtil.isShiroEnabled();
  if (!isShiroEnabled) {
    return false;
  }

  const credentials = await LoginTestUtil.getTestCredentials();
  const validUsers = Object.values(credentials).filter(cred => cred.username && cred.password);

  if (validUsers.length === 0) {
    return false;
  }

  const testUser = validUsers[0];

  const isLoginVisible = await page.locator('zeppelin-login').isVisible();
  if (isLoginVisible) {
    const loginPage = new LoginPage(page);
    await loginPage.login(testUser.username, testUser.password);

    await page.evaluate(() => {
      if (window.location.hash.includes('login')) {
        window.location.hash = '#/';
      }
    });

    try {
      await page.waitForSelector('zeppelin-login', { state: 'hidden', timeout: 30000 });
      await page.waitForSelector('text=Welcome to Zeppelin!', { timeout: 30000 });
      await page.waitForLoadState('networkidle');
      await page.waitForSelector('zeppelin-node-list', { timeout: 30000 });
      await waitForZeppelinReady(page);
      return true;
    } catch {
      return false;
    }
  }

  return false;
};

export const skipWhenAuthenticationIsStillRequired = async (page: Page): Promise<void> => {
  const loginStillVisible = await page
    .locator('zeppelin-login')
    .isVisible()
    .catch(() => false);
  test.skip(loginStillVisible, 'Authentication is enabled but no E2E test credentials are configured');
};

export const waitForZeppelinReady = async (page: Page, options: WaitForZeppelinReadyOptions = {}): Promise<void> => {
  try {
    // Enhanced wait for network idle with longer timeout for CI environments
    await page.waitForLoadState('domcontentloaded', { timeout: 45000 });

    if (await handleLoginPageIfNeeded(page, options)) {
      return;
    }

    // Wait for Angular and Zeppelin to be ready with more robust checks
    await page.waitForFunction(
      // JUSTIFIED: multi-condition OR across DOM + textContent checks; textContent not expressible via Playwright locator API
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

    const settledState = await waitForWorkspaceOrLogin(page);
    if (settledState === 'login' || (await handleLoginPageIfNeeded(page, options))) {
      return;
    }
  } catch (error) {
    throw new Error(`Zeppelin loading failed: ${String(error)}`);
  }
};

export const waitForNotebookLinks = async (page: Page, timeout: number = 30000) => {
  const locator = page.locator(NOTEBOOK_PATTERNS.LINK_SELECTOR);

  // If there are no notebook links on the page, there's no reason to wait
  const count = await locator.count();
  if (count === 0) {
    return;
  }

  await locator.first().waitFor({ state: 'visible', timeout });
};

const waitForNotebookParagraphVisible = async (page: Page, noteId: string): Promise<void> => {
  const waitOnce = async () => {
    await page.waitForURL(new RegExp(`/notebook/${noteId}`), { timeout: 15000 });
    await page.locator('zeppelin-notebook-paragraph').first().waitFor({ state: 'visible', timeout: 30000 });
  };

  try {
    await waitOnce();
  } catch {
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
    await waitForZeppelinReady(page);
    await waitOnce();
  }
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
  } catch {
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
      const notebookLink = page.locator(NOTEBOOK_PATTERNS.LINK_SELECTOR).filter({ hasText: baseName! });
      // Use the click action's built-in wait.
      await notebookLink.click({ timeout: 10000 });

      await page.waitForURL(NOTEBOOK_PATTERNS.URL_REGEX, { timeout: 20000 });
      navigationSuccessful = true;
    }
  }

  if (!navigationSuccessful) {
    throw new Error(`Failed to navigate to notebook ${noteId}`);
  }

  // Wait for notebook to be ready. Hash navigation can occasionally reach the
  // target URL before the notebook component has subscribed to the backend data;
  // a single reload keeps the same route while forcing Angular to fetch the note.
  await waitForZeppelinReady(page);
  await waitForNotebookParagraphVisible(page, noteId);
};

interface ZeppelinJsonResponse<T> {
  status: string;
  message?: string;
  body: T;
}

interface InterpreterSettingSummary {
  name?: string;
}

interface NoteSummary {
  paragraphs?: Array<{ id?: string }>;
}

const getDefaultInterpreterGroup = async (page: Page): Promise<string | undefined> => {
  const response = await page.request.get('/api/interpreter/setting', { failOnStatusCode: false });
  if (!response.ok()) {
    return undefined;
  }

  const json = (await response.json()) as ZeppelinJsonResponse<InterpreterSettingSummary[]>;
  return json.body?.find(setting => !!setting.name)?.name;
};

const createNotebookViaRest = async (
  page: Page,
  notebookName: string
): Promise<{ noteId: string; paragraphId: string }> => {
  const defaultInterpreterGroup = await getDefaultInterpreterGroup(page);
  const payload: Record<string, unknown> = {
    notePath: notebookName,
    addingEmptyParagraph: true
  };

  if (defaultInterpreterGroup) {
    payload.defaultInterpreterGroup = defaultInterpreterGroup;
  }

  const createResponse = await page.request.post('/api/notebook', {
    data: payload,
    failOnStatusCode: false
  });
  if (!createResponse.ok()) {
    throw new Error(`Create notebook REST request failed: ${createResponse.status()} ${await createResponse.text()}`);
  }

  const createJson = (await createResponse.json()) as ZeppelinJsonResponse<string>;
  const noteId = createJson.body;
  if (!noteId) {
    throw new Error(`Create notebook REST response did not include note id: ${JSON.stringify(createJson)}`);
  }

  let noteJson!: ZeppelinJsonResponse<NoteSummary>;
  await expect(async () => {
    const response = await page.request.get(`/api/notebook/${noteId}`, { failOnStatusCode: false });
    if (!response.ok()) {
      throw new Error(`Fetch notebook REST request failed: ${response.status()} ${await response.text()}`);
    }
    noteJson = (await response.json()) as ZeppelinJsonResponse<NoteSummary>;
  }).toPass({ timeout: 7500, intervals: [500, 1000, 1500, 2000, 2500] });

  const paragraphId = noteJson.body?.paragraphs?.[0]?.id;
  if (!paragraphId || !paragraphId.startsWith('paragraph_')) {
    throw new Error(`Create notebook REST response did not include paragraph id: ${JSON.stringify(noteJson.body)}`);
  }

  return { noteId, paragraphId };
};

interface CreateTestNotebookWithNameOptions {
  folderPath?: string | null;
  namePrefix?: string;
}

export const createTestNotebookWithName = async (
  page: Page,
  options: CreateTestNotebookWithNameOptions = {}
): Promise<{ noteId: string; paragraphId: string; notebookName: string; notebookPath: string }> => {
  const isRetryableError = (message: string): boolean =>
    /REST request failed: (404|409|500)\b/.test(message) ||
    message.includes('Fetch notebook REST request failed') ||
    // TODO: transient WebKit-on-Linux crash (microsoft/playwright#34450); drop once fixed upstream.
    /WebKit encountered an internal error|Target crashed/.test(message);

  const tryCreate = async () => {
    const prefix = options.namePrefix ?? 'TestNotebook';
    const notebookName = `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const notebookPath =
      options.folderPath === null ? notebookName : `${options.folderPath || E2E_TEST_FOLDER}/${notebookName}`;
    const { noteId, paragraphId } = await createNotebookViaRest(page, notebookPath);
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    return { noteId, paragraphId, notebookName, notebookPath };
  };

  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      return await tryCreate();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (attempt === 3 || !isRetryableError(message)) {
        throw new Error(`Failed to create test notebook: ${message}. Current URL: ${page.url()}`);
      }
      await page.waitForTimeout(1000 * attempt);
    }
  }

  // Unreachable: loop returns on success or throws on final attempt.
  throw new Error('createTestNotebookWithName: exhausted retries without resolution');
};

export const createTestNotebook = async (
  page: Page,
  folderPath?: string
): Promise<{ noteId: string; paragraphId: string }> => {
  const { noteId, paragraphId } = await createTestNotebookWithName(page, { folderPath });
  return { noteId, paragraphId };
};
