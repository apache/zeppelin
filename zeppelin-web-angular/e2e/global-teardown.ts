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
}

export default globalTeardown;
