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

const path = require('path');

const webRoot = path.resolve(__dirname, '../../..');
const reactRemoteRoot = path.resolve(webRoot, 'projects/zeppelin-react');
const ModuleFederationPlugin = require(
  path.join(reactRemoteRoot, 'node_modules/webpack/lib/container/ModuleFederationPlugin')
);

class RejectNotebookCoreRuntimePlugin {
  apply(compiler) {
    compiler.hooks.compilation.tap('RejectNotebookCoreRuntimePlugin', compilation => {
      compilation.hooks.finishModules.tap('RejectNotebookCoreRuntimePlugin', modules => {
        const coreRoots = [
          path.resolve(webRoot, 'projects/zeppelin-notebook-core'),
          path.resolve(webRoot, 'dist/zeppelin-notebook-core')
        ];
        const bundledCoreModules = [...modules]
          .map(module => module.resource)
          .filter(
            resource =>
              typeof resource === 'string' &&
              coreRoots.some(coreRoot => path.resolve(resource).startsWith(`${coreRoot}${path.sep}`))
          );
        if (bundledCoreModules.length > 0) {
          compilation.errors.push(
            new Error(`React remote bundled Shared Notebook Core runtime: ${bundledCoreModules.join(', ')}`)
          );
        }
      });
    });
  }
}

module.exports = {
  entry: './empty.ts',
  context: __dirname,
  resolve: {
    extensions: ['.tsx', '.ts', '.js', '.jsx'],
    modules: [path.resolve(reactRemoteRoot, 'node_modules'), path.resolve(webRoot, 'node_modules'), 'node_modules'],
    alias: {
      '@zeppelin/notebook-core': path.resolve(webRoot, 'projects/zeppelin-notebook-core/src/public-api.ts')
    }
  },
  resolveLoader: {
    modules: [path.resolve(reactRemoteRoot, 'node_modules'), path.resolve(webRoot, 'node_modules'), 'node_modules']
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: {
          loader: 'ts-loader',
          options: {
            configFile: path.resolve(__dirname, 'tsconfig.json'),
            transpileOnly: true
          }
        },
        exclude: /node_modules/
      }
    ]
  },
  output: {
    clean: true,
    path: path.resolve(__dirname, 'dist'),
    publicPath: '/assets/react/',
    scriptType: 'text/javascript',
    uniqueName: 'notebookCorePortProof'
  },
  plugins: [
    new RejectNotebookCoreRuntimePlugin(),
    new ModuleFederationPlugin({
      exposes: {
        './NotebookCorePortProbe': './NotebookCorePortProbe'
      },
      filename: 'remoteEntry.js',
      name: 'reactApp'
    })
  ]
};
