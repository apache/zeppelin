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

const ModuleFederationPlugin = require('webpack/lib/container/ModuleFederationPlugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');

module.exports = (_env, argv) => {
  const isProduction = argv.mode === 'production';
  const publicPath = isProduction ? '/assets/react/' : 'http://localhost:3001/';

  return {
    entry: './src/main.ts',
    devServer: {
      port: 3001,
      historyApiFallback: true,
      hot: false,
      liveReload: false,
      allowedHosts: 'all',
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
        'Access-Control-Allow-Headers': 'X-Requested-With, content-type, Authorization'
      },
      client: false,
      webSocketServer: false
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.js', '.jsx'],
      modules: ['node_modules', path.resolve(__dirname, '../../node_modules')],
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    resolveLoader: {
      modules: ['node_modules', path.resolve(__dirname, '../../node_modules')]
    },
    module: {
      rules: [
        {
          test: /\.tsx?$/,
          use: {
            loader: 'ts-loader',
            options: {
              transpileOnly: true,
              configFile: 'tsconfig.json'
            }
          },
          exclude: /node_modules/
        },
        {
          test: /\.css$/,
          use: ['style-loader', 'css-loader']
        }
      ]
    },
    plugins: [
      new ModuleFederationPlugin({
        name: 'reactApp',
        filename: 'remoteEntry.js',
        exposes: {
          './PublishedParagraph': './src/pages/PublishedParagraph'
        },
        shared: {
          react: {
            singleton: true,
            strictVersion: false,
            requiredVersion: '18.3.1',
            eager: true
          },
          'react-dom': {
            singleton: true,
            strictVersion: false,
            requiredVersion: '18.3.1',
            eager: true
          }
        }
      }),
      new HtmlWebpackPlugin({
        template: './src/index.html'
      }),
      {
        apply: compiler => {
          compiler.hooks.afterEmit.tap('GenerateRemoteEntryJson', () => {
            const fs = require('fs');
            const path = require('path');

            const remoteEntryJson = {
              name: 'zeppelinReact',
              type: 'module',
              version: '1.0.0',
              baseUrl: isProduction ? '/assets/react/' : 'http://localhost:3001/',
              exposes: {
                './PublishedParagraph': './PublishedParagraph.tsx'
              }
            };

            const outputDir = path.resolve(__dirname, 'dist');
            const outputPath = path.resolve(outputDir, 'remoteEntry.json');

            // Ensure directory exists
            if (!fs.existsSync(outputDir)) {
              fs.mkdirSync(outputDir, { recursive: true });
            }

            fs.writeFileSync(outputPath, JSON.stringify(remoteEntryJson, null, 2));
            console.log('Generated remoteEntry.json for Native Federation');
          });
        }
      }
    ],
    output: {
      path: path.resolve(__dirname, 'dist'),
      clean: true,
      publicPath: publicPath,
      uniqueName: 'reactApp'
    }
  };
};
