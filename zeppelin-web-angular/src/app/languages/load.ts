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

import { editor, languages } from 'monaco-editor';
import { conf as ScalaConf, language as ScalaLanguage } from './scala';

export const loadMonacoBefore = () => {
  editor.defineTheme('zeppelin-theme', {
    base: 'vs',
    inherit: true,
    rules: [],
    colors: {
      'editor.lineHighlightBackground': '#0000FF10'
    }
  });
  editor.setTheme('zeppelin-theme');
  languages.register({ id: 'scala' });
  languages.setMonarchTokensProvider('scala', ScalaLanguage);
  languages.setLanguageConfiguration('scala', ScalaConf);
};
