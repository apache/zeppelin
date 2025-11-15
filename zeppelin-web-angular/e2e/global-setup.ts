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

import * as fs from 'fs';
import { LoginTestUtil } from './models/login-page.util';

async function globalSetup() {
  console.log('Global Setup: Preparing test environment...');

  // Reset cache to ensure fresh check
  LoginTestUtil.resetCache();

  // Set up test notebook directory if specified
  await setupTestNotebookDirectory();

  // Check Shiro configuration
  const isShiroEnabled = await LoginTestUtil.isShiroEnabled();

  if (isShiroEnabled) {
    console.log('✅ Shiro.ini detected - authentication tests will run');

    // Parse and validate credentials
    const credentials = await LoginTestUtil.getTestCredentials();
    const userCount = Object.keys(credentials).length;

    console.log(`📋 Found ${userCount} test credentials in shiro.ini`);
  } else {
    console.log('⚠️  Shiro.ini not found - authentication tests will be skipped');
  }
}

async function setupTestNotebookDirectory(): Promise<void> {
  const testNotebookDir = process.env.ZEPPELIN_E2E_TEST_NOTEBOOK_DIR;

  if (!testNotebookDir) {
    console.log('No custom test notebook directory configured');
    return;
  }

  console.log(`Setting up test notebook directory: ${testNotebookDir}`);

  // Remove existing directory if it exists, then create fresh
  if (fs.existsSync(testNotebookDir)) {
    await fs.promises.rmdir(testNotebookDir, { recursive: true });
  }

  fs.mkdirSync(testNotebookDir, { recursive: true });
  fs.chmodSync(testNotebookDir, 0o777);
}

export default globalSetup;
