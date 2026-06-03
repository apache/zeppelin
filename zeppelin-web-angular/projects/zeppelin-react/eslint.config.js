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

// ESLint 9 flat config for the zeppelin-react sub-app (migrated from the
// `.eslintrc.json` that worked under ESLint 8). The Angular 21 upgrade
// (ZEPPELIN-6424) bumped the root install to ESLint 9, which dropped
// `.eslintrc.*` support entirely; this file is a 1:1 port of the legacy rule
// set so the React sources are still linted by `npm run lint:react`.
//
// The root `zeppelin-web-angular/eslint.config.js` ignores
// `projects/zeppelin-react/**`, so this config owns linting inside this
// directory exclusively.

const js = require('@eslint/js');
const globals = require('globals');
const tseslint = require('typescript-eslint');
const react = require('eslint-plugin-react');
const reactHooks = require('eslint-plugin-react-hooks');

module.exports = tseslint.config(
  {
    // == legacy `ignorePatterns`
    ignores: ['dist/**', 'node_modules/**', 'webpack.config.js']
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    // == legacy `extends`: eslint:recommended + @typescript-eslint/recommended
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      // == legacy `parserOptions`
      parserOptions: {
        ecmaFeatures: { jsx: true },
        ecmaVersion: 'latest',
        sourceType: 'module',
        project: true,
        tsconfigRootDir: __dirname
      },
      // == legacy `env: { browser: true, es2021: true }`
      globals: {
        ...globals.browser,
        ...globals.es2021
      }
    },
    plugins: {
      react,
      'react-hooks': reactHooks
    },
    settings: {
      react: { version: 'detect' }
    },
    rules: {
      // == legacy `plugin:react/recommended` + `plugin:react/jsx-runtime`
      ...react.configs.recommended.rules,
      ...react.configs['jsx-runtime'].rules,
      // == legacy `plugin:react-hooks/recommended`
      ...reactHooks.configs.recommended.rules,

      // == legacy custom `rules` (1:1 port from .eslintrc.json)
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      '@typescript-eslint/no-for-in-array': 'error',
      '@typescript-eslint/no-this-alias': 'error',
      'no-duplicate-imports': 'error',
      'no-invalid-this': 'error',
      'no-irregular-whitespace': 'error',
      'no-param-reassign': 'error',
      'no-redeclare': 'error',
      'no-sparse-arrays': 'error',
      'no-template-curly-in-string': 'error',
      'prefer-object-spread': 'error',
      'prefer-template': 'error',
      yoda: 'error',
      'react-hooks/exhaustive-deps': 'error'
    }
  }
);
