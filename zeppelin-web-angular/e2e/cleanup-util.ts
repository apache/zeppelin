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

import { BASE_URL, E2E_TEST_FOLDER } from './utils';

export const cleanupTestNotebooks = async () => {
  try {
    console.log('Cleaning up test folder via API...');

    // Get all notebooks and folders
    const response = await fetch(`${BASE_URL}/api/notebook`);
    const data = await response.json();
    if (!data.body || !Array.isArray(data.body)) {
      console.log('No notebooks found or invalid response format');
      return;
    }

    // Find the test folder
    const testFolders = data.body.filter(
      (item: { path: string }) =>
        item.path && item.path.split('/')[1] === E2E_TEST_FOLDER && !item.path.includes(`~Trash`)
    );

    if (testFolders.length === 0) {
      console.log('No test folder found to clean up');
      return;
    }

    await Promise.all(
      testFolders.map(async (testFolder: { id: string; path: string }) => {
        try {
          console.log(`Deleting test folder: ${testFolder.id} (${testFolder.path})`);

          const deleteResponse = await fetch(`${BASE_URL}/api/notebook/${testFolder.id}`, {
            method: 'DELETE'
          });

          // Although a 500 status code is generally not considered a successful response,
          // this API returns 500 even when the operation actually succeeds.
          // I'll investigate this further and create an issue.
          if (deleteResponse.status === 200 || deleteResponse.status === 500) {
            console.log(`Deleted test folder: ${testFolder.path}`);
          } else {
            console.warn(`Failed to delete test folder ${testFolder.path}: ${deleteResponse.status}`);
          }
        } catch (error) {
          console.error(`Error deleting test folder ${testFolder.path}:`, error);
        }
      })
    );

    console.log('Test folder cleanup completed');
  } catch (error) {
    if (error instanceof Error && error.message.includes('ECONNREFUSED')) {
      console.error('Failed to connect to local server. Please start the frontend server first:');
      console.error('  npm start');
      console.error(`  or make sure ${BASE_URL} is running`);
    } else {
      console.warn('Failed to cleanup test folder:', error);
    }
  }
};

if (require.main === module) {
  cleanupTestNotebooks()
    .then(() => {
      console.log('Cleanup completed successfully');
      process.exit(0);
    })
    .catch(error => {
      console.error('Cleanup failed:', error);
      process.exit(1);
    });
}
