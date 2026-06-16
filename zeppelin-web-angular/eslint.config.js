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

// ESLint 9 flat config (migrated from .eslintrc.json during the Angular 20
// line). angular-eslint / typescript-eslint meta-packages provide the flat
// presets; the rule set below is a 1:1 port of the previous eslintrc rules.
const angular = require('angular-eslint');
const tseslint = require('typescript-eslint');
const importPlugin = require('eslint-plugin-import');
const jsdoc = require('eslint-plugin-jsdoc');
const preferArrow = require('eslint-plugin-prefer-arrow');
const prettier = require('eslint-config-prettier');

module.exports = tseslint.config(
  {
    // Build output, vendored binaries and the React sub-app are never linted.
    ignores: ['dist/**', 'target/**', '.angular/**', 'coverage/**', 'node/**', 'projects/zeppelin-react/**']
  },
  {
    // Fail (not just warn) on eslint-disable directives that no longer suppress
    // anything. The flat-config default is 'warn', and `ng lint` exits 0 on
    // warnings, so stale directives would otherwise accumulate unnoticed --
    // promoting to 'error' keeps the ZEPPELIN-6426 cleanup enforced.
    linterOptions: { reportUnusedDisableDirectives: 'error' }
  },
  {
    files: ['**/*.ts'],
    // == legacy `plugin:@angular-eslint/recommended` (sets the TS parser and
    // the @angular-eslint plugin). The @typescript-eslint plugin is registered
    // separately below because tsRecommended does not bring it in.
    extends: [...angular.configs.tsRecommended],
    // == legacy `plugin:@angular-eslint/template/process-inline-templates`
    processor: angular.processInlineTemplates,
    languageOptions: {
      parserOptions: {
        project: true,
        tsconfigRootDir: __dirname
      }
    },
    plugins: {
      '@typescript-eslint': tseslint.plugin,
      import: importPlugin,
      jsdoc,
      'prefer-arrow': preferArrow
    },
    rules: {
      '@angular-eslint/component-selector': [
        'error',
        { type: ['element', 'attribute'], prefix: ['zeppelin'], style: 'kebab-case' }
      ],
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: ['zeppelin'], style: 'kebab-case' }],
      '@angular-eslint/no-forward-ref': 'off',

      // OFF since the Angular 16 upgrade. Switching from the removed
      // ng-cli-compat preset to @angular-eslint `recommended` turned this rule
      // on, but the app keeps intentionally-empty lifecycle hooks as override
      // placeholders; flagging them adds noise with no benefit.
      '@angular-eslint/no-empty-lifecycle-method': 'off',

      // OFF since the Angular 19 upgrade. v19 flipped the `standalone` default
      // to true, so `ng update` stamped `standalone: false` onto every
      // NgModule-declared component/directive/pipe. This app is deliberately
      // NgModule-based; prefer-standalone (added to the angular-eslint 19
      // recommended set) errors on exactly those declarations. Turning it on
      // would force a full standalone migration -- a separate effort.
      '@angular-eslint/prefer-standalone': 'off',

      // OFF since the Angular 20 upgrade. prefer-inject (added to the
      // angular-eslint 20 recommended set) errors on every constructor-parameter
      // injection (200+ across the app). Moving to the inject() function is a
      // large, separate refactor, so the rule stays off for now.
      '@angular-eslint/prefer-inject': 'off',

      '@angular-eslint/prefer-output-readonly': 'error',
      '@typescript-eslint/adjacent-overload-signatures': 'off',
      '@typescript-eslint/array-type': 'off',
      '@typescript-eslint/ban-tslint-comment': 'off',
      '@typescript-eslint/class-literal-property-style': 'off',
      '@typescript-eslint/consistent-generic-constructors': 'off',
      '@typescript-eslint/consistent-indexed-object-style': 'off',
      '@typescript-eslint/consistent-type-assertions': 'off',
      '@typescript-eslint/consistent-type-definitions': 'off',
      '@typescript-eslint/no-confusing-non-null-assertion': 'off',
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/no-inferrable-types': 'off',
      '@typescript-eslint/prefer-for-of': 'off',
      '@typescript-eslint/prefer-function-type': 'off',

      // Replaced the removed `ban-types` rule during the Angular 19 upgrade
      // (@typescript-eslint 8 split it into these). They keep the original
      // intent -- ban the Object/String/Number/Boolean wrapper types and the
      // unsafe `Function` type -- while still allowing `{}`, which ban-types
      // had recommended as the alternative.
      '@typescript-eslint/no-wrapper-object-types': 'error',
      '@typescript-eslint/no-unsafe-function-type': 'error',

      '@typescript-eslint/member-ordering': 'warn',
      '@typescript-eslint/explicit-member-accessibility': ['off', { accessibility: 'explicit' }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-for-in-array': 'error',
      '@typescript-eslint/naming-convention': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-this-alias': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'eol-last': 'off',
      'import/no-cycle': 'error',
      'import/no-deprecated': 'off',
      'import/no-unassigned-import': 'error',
      'import/order': 'error',
      'max-len': 'off',
      'new-parens': 'off',
      'no-bitwise': 'off',
      'no-duplicate-imports': 'error',
      'no-invalid-this': 'error',
      'no-irregular-whitespace': 'error',
      'no-magic-numbers': 'off',
      'no-param-reassign': 'error',
      'no-redeclare': 'error',
      'no-sparse-arrays': 'error',
      'no-template-curly-in-string': 'error',
      'no-trailing-spaces': 'off',
      'no-underscore-dangle': 'off',
      'prefer-arrow/prefer-arrow-functions': 'warn',
      'prefer-object-spread': 'error',
      'prefer-template': 'error',
      'quote-props': 'off',
      'space-before-function-paren': 'off',
      yoda: 'error'
    }
  },
  {
    // Library projects publish under the `lib` selector prefix, not `zeppelin`.
    files: ['projects/zeppelin-sdk/**/*.ts', 'projects/zeppelin-visualization/**/*.ts'],
    rules: {
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'lib', style: 'kebab-case' }],
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'lib', style: 'camelCase' }]
    }
  },
  {
    files: ['**/*.html'],
    // == legacy `plugin:@angular-eslint/template/recommended`
    extends: [...angular.configs.templateRecommended]
  },
  // == legacy root `extends: ["prettier"]`; disables formatting rules that
  // would conflict with Prettier. Last so it wins.
  prettier
);
