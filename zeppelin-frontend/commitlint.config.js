module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'body-max-line-length': [1, 'always', 100],
    'header-case': [1, 'always', 'kebab-case'],
    'scope-case': [1, 'always', 'kebab-case'],
    'type-enum': [
      2,
      'always',
      ['build', 'ci', 'docs', 'feat', 'fix', 'perf', 'refactor', 'release', 'style', 'test', 'chore', 'revert']
    ]
  }
};
