import { languages } from 'monaco-editor';
import { conf as ScalaConf, language as ScalaLanguage } from './scala';

export const loadMonacoLanguage = () => {
  languages.register({ id: 'scala' });
  languages.setMonarchTokensProvider('scala', ScalaLanguage);
  languages.setLanguageConfiguration('scala', ScalaConf);
};
