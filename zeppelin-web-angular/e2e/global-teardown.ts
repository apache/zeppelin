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

import { LoginTestUtil } from './models/login-page.util';

async function globalTeardown() {
  console.log('🧹 Global Teardown: Cleaning up test environment...');

  LoginTestUtil.resetCache();
  console.log('✅ Test cache cleared');

  // Clean up test notebooks that may have been left behind due to test failures
  if (!process.env.CI) {
    await cleanupTestNotebooks();
  } else {
    console.log('ℹ️ Skipping test notebook cleanup in CI environment.');
  }
}

async function cleanupTestNotebooks() {
  try {
    console.log('🗂️ Cleaning up test notebooks...');

    const baseURL = 'http://localhost:4200';

    // Get all notebooks
    const response = await fetch(`${baseURL}/api/notebook`);
    const data = await response.json();

    if (!data.body || !Array.isArray(data.body)) {
      console.log('No notebooks found or invalid response format');
      return;
    }

    // Filter notebooks that start with "Test Notebook" (created by createTestNotebook)
    const testNotebooks = data.body.filter(
      (notebook: { path: string }) => notebook.path && notebook.path.startsWith('/Test Notebook ')
    );

    if (testNotebooks.length === 0) {
      console.log('✅ No test notebooks to clean up');
      return;
    }

    console.log(`Found ${testNotebooks.length} test notebooks to delete`);

    // Delete test notebooks
    for (const notebook of testNotebooks) {
      try {
        console.log(`Deleting test notebook: ${notebook.id} (${notebook.path})`);
        const deleteResponse = await fetch(`${baseURL}/api/notebook/${notebook.id}`, {
          method: 'DELETE'
        });

        if (deleteResponse.ok) {
          console.log(`✅ Deleted: ${notebook.path}`);
        } else {
          console.warn(`⚠️ Failed to delete ${notebook.path}: ${deleteResponse.status}`);
        }

        // Small delay to avoid overwhelming the server
        await new Promise(resolve => setTimeout(resolve, 50));
      } catch (error) {
        console.warn(`⚠️ Error deleting notebook ${notebook.id}:`, error);
      }
    }

    console.log('✅ Test notebook cleanup completed');
  } catch (error) {
    console.warn('⚠️ Failed to cleanup test notebooks:', error);
  }
}

export default globalTeardown;
