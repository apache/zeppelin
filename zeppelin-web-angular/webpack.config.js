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

const MONACO_DIR = /monaco-editor[\\/]/;

module.exports = (config, options, targetOptions) => {
  config.output = {
    ...(config.output || {}),
    // Unique name for this microfrontend to avoid collisions with other apps
    uniqueName: 'shell',
    publicPath: '/',
    scriptType: 'text/javascript'
  };

  config.optimization = {
    ...(config.optimization || {}),
    // Disable runtime chunk to prevent conflicts with Module Federation's runtime
    runtimeChunk: false
  };

  config.experiments = {
    ...(config.experiments || {}),
    // Enable top-level await for async Module Federation container initialization
    topLevelAwait: true
  };

  // To avoid path conflict with websocket server path of ZeppelinServer
  config.devServer = {
    ...(config.devServer || {}),
    client: {
      ...((config.devServer && config.devServer.client) || {}),
      webSocketURL: { pathname: '/wds-ws' }
    },
    webSocketServer: {
      type: 'ws',
      options: { path: '/wds-ws' }
    }
  };

  // monaco-editor imports `.css` files from its own JS modules. Angular 14's
  // build-angular CSS pipeline (postcss + mini-css-extract) does not handle
  // CSS requested from node_modules JS, and chaining style-loader on top of
  // its rule produces a postcss collision. Exclude monaco from the existing
  // CSS rules and add a dedicated rule that injects styles at runtime.
  config.module = config.module || { rules: [] };
  config.module.rules = config.module.rules || [];
  for (const rule of config.module.rules) {
    if (!rule || !rule.test) continue;
    const testStr = rule.test.toString();
    if (testStr.includes('css') || testStr.includes('CSS')) {
      const existing = rule.exclude ? (Array.isArray(rule.exclude) ? rule.exclude : [rule.exclude]) : [];
      rule.exclude = [...existing, MONACO_DIR];
    }
  }
  config.module.rules.push({
    test: /\.css$/,
    include: MONACO_DIR,
    use: ['style-loader', 'css-loader']
  });

  config.plugins = config.plugins || [];
  config.plugins.push(
    new ModuleFederationPlugin({
      name: 'shell',
      remotes: {
        reactApp: 'reactApp@http://localhost:3001/remoteEntry.js'
      }
    })
  );
  config.plugins.push(
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
  );

  return config;
};
