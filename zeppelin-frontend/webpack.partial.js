const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

module.exports = {
  plugins: [
    new MonacoWebpackPlugin({
      languages: [
        'bat', 'cpp', 'csharp', 'csp', 'css', 'dockerfile', 'go', 'handlebars', 'html',  'java', 'javascript', 'json',
        'less', 'lua', 'markdown', 'mysql', 'objective', 'perl', 'pgsql', 'php', 'powershell', 'python', 'r', 'ruby',
        'rust', 'scheme', 'scss', 'shell', 'sql', 'swift', 'typescript', 'vb', 'xml', 'yaml'
      ],
      features: ['!accessibilityHelp']
    })
  ]
};

