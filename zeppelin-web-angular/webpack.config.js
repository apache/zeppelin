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
const webpack = require('@angular-devkit/build-angular/node_modules/webpack');
const ModuleFederationPlugin = webpack.container.ModuleFederationPlugin;

module.exports = {
  output: {
    // Unique name for this microfrontend to avoid collisions with other apps
    uniqueName: 'shell',
    // Auto-detect publicPath at runtime for Module Federation dynamic imports
    publicPath: 'auto'
  },
  optimization: {
    // Disable runtime chunk to prevent conflicts with Module Federation's runtime
    runtimeChunk: false
  },
  experiments: {
    // Enable top-level await for async Module Federation container initialization
    topLevelAwait: true
  },
  // To avoid path conflict with websocket server path of ZeppelinServer
  devServer: {
    client: {
      webSocketURL: {
        pathname: '/wds-ws'
      }
    },
    webSocketServer: {
      type: 'ws',
      options: {
        path: '/wds-ws'
      }
    }
  },
  plugins: [
    new ModuleFederationPlugin({
      name: 'shell',
      remotes: {
        reactApp: 'reactApp@http://localhost:3001/remoteEntry.js'
      }
    }),
    new MonacoWebpackPlugin({
      languages: [
        'bat',
        'cpp',
        'csharp',
        'csp',
        'css',
        'dockerfile',
        'go',
        'handlebars',
        'html',
        'java',
        'javascript',
        'json',
        'less',
        'lua',
        'markdown',
        'mysql',
        'objective',
        'perl',
        'pgsql',
        'php',
        'powershell',
        'python',
        'r',
        'ruby',
        'rust',
        'scheme',
        'scss',
        'shell',
        'sql',
        'swift',
        'typescript',
        'vb',
        'xml',
        'yaml'
      ],
      features: ['!accessibilityHelp']
    })
  ]
};
