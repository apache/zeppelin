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
        '@': path.resolve(__dirname, 'src'),
        '@zeppelin/sdk': path.resolve(__dirname, '../zeppelin-sdk/src')
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
      // No `shared` scope: the shell bundles no React and never calls container.init.
      // This remote is the only participant, so there is nothing to dedupe against. Re-add it once a second exists.
      new ModuleFederationPlugin({
        name: 'reactApp',
        filename: 'remoteEntry.js',
        exposes: {
          './PublishedParagraph': './src/pages/PublishedParagraph',
          './ParagraphFooter': './src/components/paragraph/ParagraphFooter'
        }
      }),
      new HtmlWebpackPlugin({
        template: './src/index.html'
      })
    ],
    output: {
      path: path.resolve(__dirname, 'dist'),
      clean: true,
      publicPath: publicPath,
      uniqueName: 'reactApp',
      scriptType: 'text/javascript'
    }
  };
};
