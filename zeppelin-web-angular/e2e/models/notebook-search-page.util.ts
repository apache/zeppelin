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

import { expect, Page } from '@playwright/test';

export const waitForSearchIndex = async (page: Page, query: string, documentId: string): Promise<void> => {
  await expect
    .poll(
      async () => {
        const response = await page.request.get('/api/notebook/search', {
          params: { q: query },
          failOnStatusCode: true
        });
        return response.json();
      },
      { message: `Search index contains ${documentId}`, timeout: 30000, intervals: [250, 500, 1000] }
    )
    .toMatchObject({
      body: expect.arrayContaining([expect.objectContaining({ id: documentId })])
    });
};
