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

import { exec } from 'child_process';
import { promisify } from 'util';
import { LoginTestUtil } from './models/login-page.util';

const execAsync = promisify(exec);

const globalTeardown = async () => {
  console.log('Global Teardown: Cleaning up test environment...');

  LoginTestUtil.resetCache();
  console.log('Test cache cleared');

  if (!process.env.CI) {
    console.log('Running cleanup script: npx tsx e2e/cleanup-util.ts');

    try {
      // The reason for calling it this way instead of using the function directly
      // is to maintain compatibility between ESM and CommonJS modules.
      const { stdout, stderr } = await execAsync('npx tsx e2e/cleanup-util.ts');
      if (stdout) {
        console.log(stdout);
      }
      if (stderr) {
        console.error(stderr);
      }
    } catch (error) {
      console.error('Cleanup script failed:', error);
    }
  }
};

export default globalTeardown;
