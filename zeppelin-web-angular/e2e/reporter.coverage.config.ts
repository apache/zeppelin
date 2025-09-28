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

import { join } from 'path';
import { getCoverageTransformPaths } from './utils';

const outputPath = join(__dirname, '..', 'playwright-coverage');

export default {
  rootPath: join(__dirname, '..'),
  outputPath,
  testMatch: [/\.component$/],
  excludes: [/\.spec\.ts$/, /\.module\.ts$/, /\.guard\.ts$/, /\.routing\.ts$/, /\.html$/, /\.less$/, /\.css$/],
  // Transform configuration for coverage instrumentation
  // Specifies which component files to instrument for coverage tracking
  transform: getCoverageTransformPaths()
};
