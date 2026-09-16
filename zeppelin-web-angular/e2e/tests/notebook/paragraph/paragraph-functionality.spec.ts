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
import { NotebookParagraphPage } from 'e2e/models/notebook-paragraph-page';
import { NotebookKeyboardPage } from 'e2e/models/notebook-keyboard-page';
import {
  addPageAnnotation,
  addPageAnnotationBeforeEach,
  createTestNotebook,
  performLoginIfRequired,
  PAGES,
  setParagraphText,
  waitForZeppelinReady
} from '../../../utils';

test.describe('Notebook Paragraph Functionality', () => {
  // JUSTIFIED: this legacy spec stores its page object and notebook id in describe scope.
  test.describe.configure({ mode: 'default' });
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);

  let paragraphPage: NotebookParagraphPage;
  let testNotebook: { noteId: string; paragraphId: string };

  test.beforeEach(async ({ page }) => {
    await page.goto('/#/');
    await waitForZeppelinReady(page);
    await performLoginIfRequired(page);

    testNotebook = await createTestNotebook(page);
    paragraphPage = new NotebookParagraphPage(page);

    await page.goto(`/#/notebook/${testNotebook.noteId}`);
    // Paragraphs arrive over the WebSocket, so 'networkidle' can resolve before they render.
    // Wait for the paragraph to mount so tests do not act on a bare page.
    await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 30000 });
  });

  test('should display the paragraph container and control panel', async () => {
    await expect(paragraphPage.paragraphContainer).toBeVisible();
    await expect(paragraphPage.controlPanel).toBeVisible();
  });

  test(
    'should reflect user edits in the code editor state and rendered lines',
    { tag: '@NB-PARITY-003' },
    async ({ page }, testInfo) => {
      addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CODE_EDITOR, testInfo);
      const keyboard = new NotebookKeyboardPage(page);

      await test.step('Given the paragraph is in edit mode', async () => {
        await paragraphPage.doubleClickToEdit();
        await expect(paragraphPage.codeEditor).toBeVisible();
        await paragraphPage.editorInput.focus();
        await expect(paragraphPage.editorInput).toBeFocused();
      });

      await test.step('When the user replaces the paragraph with five lines', async () => {
        await keyboard.pressSelectAll();
        await page.keyboard.type('%md\nline one\nline two\nline three\nline four');
      });

      await test.step('Then Monaco renders the text and marks the editor focused and dirty', async () => {
        await expect(paragraphPage.editorViewLines).toContainText('line four');
        await expect(paragraphPage.editorLines).toHaveCount(5);
        await expect(paragraphPage.codeEditorHost).toHaveClass(/\bfocused\b/);
        await expect(paragraphPage.codeEditorHost).toHaveClass(/\bdirty\b/);

        await page.keyboard.press('Escape');
        await expect(paragraphPage.codeEditorHost).not.toHaveClass(/\bfocused\b/);
      });
    }
  );

  test(
    'should insert default paragraphs above and below the original paragraph',
    { tag: '@NB-PARITY-004' },
    async ({ page }, testInfo) => {
      addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_ADD_PARAGRAPH, testInfo);
      const keyboard = new NotebookKeyboardPage(page);
      const originalText = 'Original paragraph marker';

      await test.step('Given one paragraph with distinctive text', async () => {
        await keyboard.setCodeEditorContent(`%md\n${originalText}`);
        await expect(paragraphPage.paragraphContainers).toHaveCount(1);
      });

      await test.step('When the trailing Add Paragraph control is clicked', async () => {
        await paragraphPage.clickAddParagraphBelow();
      });

      await test.step('Then a new paragraph is inserted below the original', async () => {
        await expect(paragraphPage.paragraphContainers).toHaveCount(2);
        // JUSTIFIED: the first rendered editor is the original paragraph after inserting below.
        await expect(paragraphPage.editorViewLinesAll.first()).toContainText(originalText);
        // JUSTIFIED: the last rendered editor is the newly inserted default paragraph.
        await expect(paragraphPage.editorViewLinesAll.last()).toHaveText('%md');
      });

      await test.step('When the leading Add Paragraph control is clicked', async () => {
        await paragraphPage.clickAddParagraphAbove();
      });

      await test.step('Then a default paragraph is inserted above the original', async () => {
        await expect(paragraphPage.paragraphContainers).toHaveCount(3);
        // JUSTIFIED: inserting above moves the original paragraph to the second position.
        await expect(paragraphPage.editorViewLinesAll.nth(1)).toContainText(originalText);
        // JUSTIFIED: the first rendered editor is the newly inserted default paragraph.
        await expect(paragraphPage.editorViewLinesAll.first()).toHaveText('%md');
      });
    }
  );

  test(
    'should accumulate interpreter output while the paragraph is running',
    { tag: '@NB-PARITY-022' },
    async ({ page }) => {
      await test.step('Given a shell paragraph that emits three delayed output chunks', async () => {
        await setParagraphText(
          page,
          testNotebook.noteId,
          testNotebook.paragraphId,
          '%sh\necho first; sleep 3; echo second; sleep 5; echo third'
        );
        await page.reload();
        await expect(paragraphPage.paragraphContainer).toBeVisible({ timeout: 30000 });
      });

      await test.step('When the paragraph runs', async () => {
        await paragraphPage.runParagraph();
      });

      await test.step('Then output accumulates before the paragraph finishes', async () => {
        await expect(paragraphPage.resultDisplay).toContainText('first', { timeout: 30000 });
        await expect(paragraphPage.status).toHaveText('RUNNING');
        await expect(paragraphPage.resultDisplay).toContainText(/first\s+second/, { timeout: 10000 });
        await expect(paragraphPage.status).toHaveText('RUNNING');
        await expect(paragraphPage.resultDisplay).toContainText(/first\s+second\s+third/, { timeout: 10000 });
        await expect(paragraphPage.status).toHaveText('FINISHED');
        await expect(paragraphPage.resultDisplay).toHaveText('first\nsecond\nthird\n');
      });
    }
  );

  test('should expose the settings available for a single paragraph', async ({}, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);

    await paragraphPage.openSettingsDropdown();

    await expect(paragraphPage.settingsMenu).toBeVisible();
    await expect(paragraphPage.paragraphIdMenuItem).toHaveText(/paragraph_\d+_\d+/);
    await expect(paragraphPage.settingsMenuItem('Width')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Font size')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Insert new')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Clone paragraph')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Clear output')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Show Title')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Show line numbers')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Disable run')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Remove')).toHaveCount(0);
    await expect(paragraphPage.settingsMenuItem('Move paragraph up')).toHaveCount(0);
    await expect(paragraphPage.settingsMenuItem('Move paragraph down')).toHaveCount(0);
  });

  test('should report running and finished execution state', { tag: '@NB-PARITY-021' }, async ({ page }, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_FOOTER, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_PROGRESS, testInfo);
    const keyboard = new NotebookKeyboardPage(page);

    await test.step('Given a paragraph that runs long enough to observe', async () => {
      await keyboard.setCodeEditorContent('%python\nimport time\ntime.sleep(2)\nprint("lifecycle complete")');
      await expect(paragraphPage.status).toHaveText('READY');
    });

    await test.step('When the paragraph starts', async () => {
      await paragraphPage.runParagraph();
    });

    await test.step('Then running controls, progress, and elapsed time are shown', async () => {
      await expect(paragraphPage.status).toHaveText('RUNNING', { timeout: 60000 });
      await expect(paragraphPage.cancelButton).toBeVisible();
      await expect(paragraphPage.progressBar).toBeVisible();
      await expect(paragraphPage.elapsedTime).toHaveText(/^Started .+ ago\.$/, { timeout: 15000 });
    });

    await test.step('Then completion removes progress and shows result timing', async () => {
      await expect(paragraphPage.status).toHaveText('FINISHED', { timeout: 60000 });
      await expect(paragraphPage.resultDisplay).toContainText('lifecycle complete');
      await expect(paragraphPage.progressIndicator).toHaveCount(0);
      await paragraphPage.paragraphContainer.hover();
      await expect(paragraphPage.executionTime).toHaveText(/^Took .+\. Last updated by .+ at .+\./);
    });

    await test.step('When the finished paragraph is edited', async () => {
      await paragraphPage.editorInput.focus();
      await page.keyboard.type(' ');
      await page.keyboard.press('Escape');
    });

    await test.step('Then the previous result is marked outdated and can be cleared', async () => {
      await expect(paragraphPage.executionTime).toContainText('(outdated)');
      await paragraphPage.openSettingsDropdown();
      await paragraphPage.settingsMenuItem('Clear output').click();
      await expect(paragraphPage.resultDisplay).toHaveCount(0);
    });
  });

  test('should edit a paragraph title through the elastic input', async ({}, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_ELASTIC_INPUT, testInfo);

    await test.step('Given paragraph titles are enabled', async () => {
      await paragraphPage.openSettingsDropdown();
      await paragraphPage.settingsMenuItem('Show Title').click();
      await expect(paragraphPage.paragraphTitleText).toHaveText('Untitled');
    });

    await test.step('When a title is entered and committed with Enter', async () => {
      await paragraphPage.paragraphTitleText.click();
      await paragraphPage.paragraphTitleInput.fill('My paragraph');
      await paragraphPage.paragraphTitleInput.press('Enter');
    });

    await test.step('Then the committed title replaces the input', async () => {
      await expect(paragraphPage.paragraphTitleInput).toHaveCount(0);
      await expect(paragraphPage.paragraphTitleText).toHaveText('My paragraph');
    });

    await test.step('When a different title is cancelled with Escape', async () => {
      await paragraphPage.paragraphTitleText.click();
      await paragraphPage.paragraphTitleInput.fill('Temporary title');
      await paragraphPage.paragraphTitleInput.press('Escape');
    });

    await test.step('Then the committed title remains unchanged', async () => {
      await expect(paragraphPage.paragraphTitleText).toHaveText('My paragraph');
      await expect(paragraphPage.paragraphTitleInput).toHaveCount(0);
    });

    await test.step('When a longer title is entered and the input loses focus', async () => {
      await paragraphPage.paragraphTitleText.click();
      const longTitle = 'A much longer paragraph title that commits on blur';
      await paragraphPage.paragraphTitleInput.fill(longTitle);
      await paragraphPage.paragraphTitleInput.blur();
      await expect(paragraphPage.paragraphTitleText).toHaveText(longTitle);
      await expect(paragraphPage.paragraphTitleInput).toHaveCount(0);
    });
  });

  test('should clone a paragraph with its editor content', async ({ page }, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    const keyboard = new NotebookKeyboardPage(page);
    const cloneMarker = 'Distinctive clone marker';

    await keyboard.setCodeEditorContent(`%md\n${cloneMarker}`);
    await paragraphPage.openSettingsDropdown();
    await paragraphPage.settingsMenuItem('Clone paragraph').click();

    await expect(paragraphPage.paragraphContainers).toHaveCount(2);
    await expect(paragraphPage.editorViewLinesAll).toHaveText([
      /Distinctive\s+clone\s+marker/,
      /Distinctive\s+clone\s+marker/
    ]);
  });

  test('should insert, move, and remove paragraphs through the settings menu', async ({ page }, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    const keyboard = new NotebookKeyboardPage(page);
    const originalMarker = 'Paragraph to move';

    await keyboard.setCodeEditorContent(`%md\n${originalMarker}`);
    await paragraphPage.openSettingsDropdown();
    await paragraphPage.settingsMenuItem('Insert new').click();
    await expect(paragraphPage.paragraphContainers).toHaveCount(2);

    await paragraphPage.openSettingsDropdown();
    await expect(paragraphPage.settingsMenuItem('Remove')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Move paragraph down')).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Move paragraph up')).toHaveCount(0);
    await paragraphPage.settingsMenuItem('Move paragraph down').click();

    // JUSTIFIED: after moving the original first paragraph down, its editor is last.
    await expect(paragraphPage.editorViewLinesAll.last()).toContainText(originalMarker);

    // JUSTIFIED: remove the moved paragraph through its last settings trigger.
    await paragraphPage.openSettingsDropdown(paragraphPage.settingsDropdowns.last());
    await paragraphPage.settingsMenuItem('Remove').click();
    await expect(paragraphPage.confirmButton).toBeVisible();
    await paragraphPage.confirmButton.click();
    await expect(paragraphPage.paragraphContainers).toHaveCount(1);
    // JUSTIFIED: removing the moved original leaves the inserted default paragraph first.
    await expect(paragraphPage.editorViewLinesAll.first()).toHaveText('%md');
  });

  test('should toggle paragraph code, output, line numbers, and run state', async ({ page }, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CODE_EDITOR, testInfo);

    await paragraphPage.toggleEditorButton.click();
    await expect(paragraphPage.codeEditor).toHaveCount(0);
    await paragraphPage.toggleEditorButton.click();
    await expect(paragraphPage.codeEditor).toBeVisible();

    await paragraphPage.toggleOutputButton.click();
    await expect(paragraphPage.dynamicForms).toHaveCount(0);
    await paragraphPage.toggleOutputButton.click();
    await expect(paragraphPage.dynamicForms).toHaveCount(1);

    await paragraphPage.openSettingsDropdown();
    await paragraphPage.settingsMenuItem('Show line numbers').click();
    await expect(paragraphPage.lineNumbers).toBeVisible();
    await expect(paragraphPage.settingsMenuItem('Hide line numbers')).toBeVisible();
    await paragraphPage.settingsMenuItem('Hide line numbers').click();
    await expect(paragraphPage.lineNumbers).toHaveCount(0);
    await page.keyboard.press('Escape');

    await paragraphPage.openSettingsDropdown();
    await paragraphPage.settingsMenuItem('Disable run').click();
    await expect(paragraphPage.runButton).toHaveCount(0);
    await expect(paragraphPage.settingsMenuItem('Enable run')).toBeVisible();
    await paragraphPage.settingsMenuItem('Enable run').click();
    await expect(paragraphPage.runButton).toBeVisible();
  });

  test('should disable insertion and expose cancellation state while running', async ({ page }, testInfo) => {
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_ADD_PARAGRAPH, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_CONTROL, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_FOOTER, testInfo);
    addPageAnnotation(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH_PROGRESS, testInfo);
    const keyboard = new NotebookKeyboardPage(page);

    const code = '%python\nimport time\ntime.sleep(10)\nprint("Done")';
    await keyboard.setCodeEditorContent(code);
    await expect.poll(() => keyboard.getParagraphTextByIndex(0)).toBe(code);
    await paragraphPage.runAllButton.click();
    await expect(paragraphPage.confirmButton).toBeVisible();
    await paragraphPage.confirmButton.click();

    await expect(paragraphPage.cancelButton).toBeVisible({ timeout: 10000 });
    await expect(paragraphPage.status).toHaveText('RUNNING', { timeout: 60000 });
    await expect(paragraphPage.progressBar).toBeVisible();
    await expect(paragraphPage.elapsedTime).toHaveText(/^Started .+ ago\.$/, { timeout: 15000 });
    await expect(paragraphPage.addParagraphAboveLink).toHaveClass(/\bdisabled\b/);
    await expect(paragraphPage.addParagraphBelowLink).toHaveClass(/\bdisabled\b/);

    await paragraphPage.clickAddParagraphBelow();
    await expect(paragraphPage.paragraphContainers).toHaveCount(1);

    await paragraphPage.cancelButton.click();
    await expect(paragraphPage.status).toHaveText('ABORT', { timeout: 30000 });
    await expect(paragraphPage.progressIndicator).toHaveCount(0);
    await expect(paragraphPage.elapsedTime).toHaveCount(0);
  });
});
