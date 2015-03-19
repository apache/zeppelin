# Zeppelin Style Guide

This is a quick guide on customizing style for the Zeppelin web interface (zeppelin-web)

### Look and Feel
app/styles/looknfeel  
Overall look and theme of the Zeppelin notebook page can be customized here.

### Code Syntax Highlighting
There are two parts to code highlighting. First, Zeppelin uses the Ace Editor for its note paragraphs. Color style for this can be changed by setting theme on the editor instance. Second, Zeppelin's Markdown interpreter calls markdown4j to emit HTML, and such content may contain &lt;pre&gt;&lt;code&gt; tags that can be consumed by Highlight.js.

#### Theme on Ace Editor
app/scripts/controllers/paragraph.js  
Call setTheme on the editor with the theme path/name.  
[Setting Theme on Ace Documentation](http://ace.c9.io/#nav=howto)  
[List of themes on GitHub](https://github.com/ajaxorg/ace/tree/master/lib/ace/theme)

#### Style for Markdown Code Blocks
Highlight.js parses and converts &lt;pre&gt;&lt;code&gt; blocks from markdown4j into keywords and language syntax with proper styles. It also attempts to infer the best fitting language if it is not provided. The visual style can be changed by simply including the desired [stylesheet](https://github.com/components/highlightjs/tree/master/styles) into app/index.html. See the next section on build.

Note that code block background color is overriden in app/styles/notebook.css (look for .paragraph .tableDisplay .hljs).

#### Build changes
bower.json  
In the override section at the bottom, include the Highlightjs stylesheet (eg. styles/github.css)  
For the selected Ace Editor theme script, include it in the override section. (eg. src-noconflict/theme-github.js)  
(bower will automatically add the appropriate .js and .css in app/index.html)
```
                  "src-noconflict/mode-sql.js",
                  "src-noconflict/mode-markdown.js",
                  "src-noconflict/keybinding-emacs.js",
                 "src-noconflict/ext-language_tools.js",
+                 "src-noconflict/theme-github.js"],
       "version": "1.1.8",
       "name": "ace-builds"
    },
    "highlightjs": {
        "main": ["highlight.pack.js",
+                 "styles/github.css"],
      "version": "8.4.0",
      "name": "highlightjs"
     }
```

Gruntfile.js  
Highlight.js style - depends on the style, a few themes have jpg - if so, one must copy them with Grunt.

### Example - change Ace Editor theme to monokai

app/scripts/controllers/paragraph.js
```
-      $scope.editor.setTheme('ace/theme/github');
+      $scope.editor.setTheme('ace/theme/monokai');
```

bower.json
```
-                 "src-noconflict/theme-github.js"],
+                 "src-noconflict/theme-monokai.js"],
```

Note that for certain themes, like monokai, with a dark background, you will need to update app/styles/notebook.css, .paragraphAsIframe .editor and .paragraph .editor for the background color.  
