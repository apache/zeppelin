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

import { expect, Page, test } from '@playwright/test';
import { createTestNotebookWithName } from '../../utils';

const testData = {
  textInFirstP: 'text word text',
  textInSecondP: 'text tete tt'
};

const countSubstringOccurrence = (text: string, substring: string): number =>
  (text.match(new RegExp(substring, 'g')) || []).length;

const expectedMatchCount = (substring: string): number =>
  countSubstringOccurrence(testData.textInFirstP, substring) +
  countSubstringOccurrence(testData.textInSecondP, substring);

const waitForClassicNotebookReady = async (page: Page, noteId: string) => {
  await page.goto(`/classic/#/notebook/${noteId}`, { waitUntil: 'domcontentloaded' });
  // JUSTIFIED: Classic note setup creates a single first paragraph; this is the render gate for that paragraph.
  await expect(page.locator('div[ng-controller="ParagraphCtrl"]').first()).toBeVisible({ timeout: 30000 });
  // JUSTIFIED: The first Ace textarea belongs to the first paragraph created with the note.
  await expect(page.locator('.ace_text-input').first()).toBeAttached({ timeout: 30000 });
};

const fillAceEditor = async (page: Page, index: number, text: string) => {
  // JUSTIFIED: The tests intentionally fill paragraph editors by creation order: first paragraph, then inserted second paragraph.
  const editor = page.locator('.ace_editor').nth(index);
  await editor.click();
  await page.keyboard.type(text);
  await expect(editor.locator('.ace_line', { hasText: text })).toBeVisible({ timeout: 15000 });
};

const makeTestParagraphs = async (page: Page) => {
  await fillAceEditor(page, 0, testData.textInFirstP);

  await page.locator('.new-paragraph.last-paragraph').click();
  await expect(page.locator('.ace_editor')).toHaveCount(2, { timeout: 15000 });
  await fillAceEditor(page, 1, testData.textInSecondP);
};

const openSearchBoxByShortcut = async (page: Page) => {
  await page.keyboard.press('Control+Alt+F');
  await expect(page.locator('.search-dropdown')).toBeVisible({ timeout: 10000 });
};

const findInput = (page: Page) => page.locator('#findInput');
const replaceInput = (page: Page) => page.locator('.search-group', { hasText: 'Replace' }).locator('input');
const matchesElement = (page: Page) => page.locator('.search-group .after-input');
const nextOccurrenceButton = (page: Page) => page.locator('.search-group button[ng-click="nextOccurrence()"]');
const prevOccurrenceButton = (page: Page) => page.locator('.search-group button[ng-click="prevOccurrence()"]');
const replaceButton = (page: Page) => page.locator('.search-group button[ng-click="replace()"]');
const replaceAllButton = (page: Page) => page.locator('.search-group button[ng-click="replaceAll()"]');

const setFindText = async (page: Page, text: string) => {
  await findInput(page).fill(text);
};

const waitForMatches = async (page: Page, current: number, amount: number) => {
  await expect(matchesElement(page)).toHaveText(`${current} of ${amount}`, { timeout: 15000 });
};

const markerCount = async (page: Page): Promise<number> =>
  page.locator('.ace_marker-layer div.ace_selected-word, .ace_marker-layer div.ace_selection').count();

test.describe('Classic search block', () => {
  test.beforeEach(async ({ page }) => {
    const { noteId } = await createTestNotebookWithName(page, {
      namePrefix: 'ClassicSearchBlock'
    });
    await waitForClassicNotebookReady(page, noteId);
  });

  test('shortcut works', async ({ page }) => {
    // JUSTIFIED: The keyboard shortcut is scoped to the currently focused first paragraph editor.
    await page.locator('.ace_editor').first().click();
    await openSearchBoxByShortcut(page);
  });

  test('correct count of selections', async ({ page }) => {
    await makeTestParagraphs(page);
    await openSearchBoxByShortcut(page);

    const textToFind = 'te';
    const matchesCount = expectedMatchCount(textToFind);
    await setFindText(page, textToFind);

    await waitForMatches(page, 1, matchesCount);
    await expect.poll(() => markerCount(page), { timeout: 15000 }).toBe(matchesCount + 1);
  });

  test('correct matches count number', async ({ page }) => {
    await makeTestParagraphs(page);
    await openSearchBoxByShortcut(page);

    let textToFind = 't';
    await setFindText(page, textToFind);
    await waitForMatches(page, 1, expectedMatchCount(textToFind));

    textToFind = 'te';
    await setFindText(page, textToFind);
    await waitForMatches(page, 1, expectedMatchCount(textToFind));
  });

  test('counter increase and decrease correctly', async ({ page }) => {
    await makeTestParagraphs(page);
    await openSearchBoxByShortcut(page);

    const textToFind = 'te';
    const matchesCount = expectedMatchCount(textToFind);
    await setFindText(page, textToFind);
    await waitForMatches(page, 1, matchesCount);

    await nextOccurrenceButton(page).click();
    await waitForMatches(page, matchesCount > 1 ? 2 : 1, matchesCount);

    await prevOccurrenceButton(page).click();
    await waitForMatches(page, 1, matchesCount);

    await prevOccurrenceButton(page).click();
    await waitForMatches(page, matchesCount, matchesCount);
  });

  test('matches count changes correctly after replace', async ({ page }) => {
    await makeTestParagraphs(page);
    await openSearchBoxByShortcut(page);

    const textToFind = 'te';
    const matchesCount = expectedMatchCount(textToFind);
    await setFindText(page, textToFind);
    await waitForMatches(page, 1, matchesCount);
    await replaceInput(page).fill('ABC');

    await replaceButton(page).click();
    await waitForMatches(page, 1, matchesCount - 1);

    await prevOccurrenceButton(page).click();
    await replaceButton(page).click();
    await waitForMatches(page, 1, matchesCount - 2);
  });

  test('replace all works correctly', async ({ page }) => {
    await makeTestParagraphs(page);
    await openSearchBoxByShortcut(page);

    const textToFind = 'te';
    await setFindText(page, textToFind);
    await waitForMatches(page, 1, expectedMatchCount(textToFind));
    await replaceInput(page).fill('ABC');

    await replaceAllButton(page).click();
    await waitForMatches(page, 0, 0);
  });
});
