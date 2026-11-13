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
import { addPageAnnotationBeforeEach, createNotebookViaRest, PAGES, setParagraphText } from '../../../utils';

test.describe('Notebook Paragraph Dynamic Forms', () => {
  test.setTimeout(60000);
  test.use({ actionTimeout: 10000, navigationTimeout: 10000 });
  test.skip(
    process.env.ZEPPELIN_E2E_REQUIRE_SPARK !== 'true',
    'Not run: requires a server with Spark; set ZEPPELIN_E2E_REQUIRE_SPARK=true to require execution'
  );
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.NOTEBOOK_PARAGRAPH);

  test('should display dynamic forms and execute with edited values', async ({ page }, testInfo) => {
    const { noteId, paragraphId } = await createNotebookViaRest(
      page,
      `E2E_TEST_FOLDER/DynamicForms_${Date.now()}_${testInfo.workerIndex}`
    );
    try {
      await setParagraphText(
        page,
        noteId,
        paragraphId,
        `%spark
println("Name: " + z.input("name", "World"))
println("Age: " + z.select("age", "1", Seq(("1", "Under 18"), ("2", "18-65"), ("3", "Over 65"))))`
      );
      // Keep edits local until Run is clicked, so each output belongs to one explicit execution.
      const configure = await page.request.put(`/api/notebook/${noteId}/paragraph/${paragraphId}/config`, {
        data: { runOnSelectionChange: false }
      });
      await expect(configure).toBeOK();
      await page.goto(`/#/notebook/${noteId}`);
      const paragraph = new NotebookParagraphPage(page);
      await expect(paragraph.paragraphContainer).toBeVisible({ timeout: 10000 });
      const runAndExpectOutput = async (output: string, timeout: number) => {
        await paragraph.runParagraph();
        // Stop waiting on interpreter errors, not only when the expected output arrives.
        await paragraph.resultDisplay
          .filter({ hasText: output })
          .or(paragraph.status.filter({ hasText: /^(ERROR|ABORT)$/ }))
          .waitFor({ state: 'visible', timeout });
        await expect(paragraph.status).toHaveText('FINISHED', { timeout: 1000 });
        // Spark may prepend compiler warnings; the form values and successful execution are the contract.
        await expect(paragraph.resultDisplay).toContainText(output, { timeout: 1000 });
      };

      await test.step('Spark creates a text input and a select with their default values', async () => {
        await runAndExpectOutput('Name: World\nAge: 1\n', 30000);
        await expect(paragraph.dynamicFormTextInput).toHaveValue('World', { timeout: 5000 });
        await expect(paragraph.dynamicFormSelect).toHaveText('Under 18', { timeout: 5000 });
      });

      await test.step('Editing both forms changes the next Spark execution output', async () => {
        await paragraph.dynamicFormTextInput.fill('Zeppelin');
        await expect(paragraph.dynamicFormTextInput).toHaveValue('Zeppelin', { timeout: 1000 });
        await paragraph.dynamicFormSelect.click();
        await page.getByText('18-65', { exact: true }).click();
        await expect(paragraph.dynamicFormTextInput).toHaveValue('Zeppelin', { timeout: 1000 });
        await expect(paragraph.dynamicFormSelect).toHaveText('18-65', { timeout: 1000 });
        // Distinct output cannot be satisfied by the previous FINISHED execution.
        await runAndExpectOutput('Name: Zeppelin\nAge: 2\n', 10000);
      });
    } finally {
      const deleted = await page.request.delete(`/api/notebook/${noteId}`, { timeout: 5000 });
      await expect(deleted).toBeOK();
    }
  });
});
