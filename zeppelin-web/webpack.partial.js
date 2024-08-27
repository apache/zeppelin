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

const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

module.exports = {
  plugins: [
    new MonacoWebpackPlugin({
      languages: [
        'bat', 'cpp', 'csharp', 'csp', 'css', 'dockerfile', 'go', 'handlebars', 'html',  'java', 'javascript', 'json',
        'less', 'lua', 'markdown', 'mysql', 'objective', 'perl', 'pgsql', 'php', 'powershell', 'python', 'r', 'ruby',
        'rust', 'scheme', 'scss', 'shell', 'sql', 'swift', 'typescript', 'vb', 'xml', 'yaml'
      ],
      features: ['!accessibilityHelp']
    })
  ]
};

