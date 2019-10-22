import { conf as ScalaConf, language as ScalaLanguage } from './scala';

export const loadMonacoLanguage = () => {
  monaco.languages.register({ id: 'scala' });
  monaco.languages.setMonarchTokensProvider('scala', ScalaLanguage);
  monaco.languages.setLanguageConfiguration('scala', ScalaConf);
};
