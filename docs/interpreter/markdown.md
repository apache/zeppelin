---
layout: page
title: "Markdown Interpreter"
description: "Markdown Interpreter"
group: manual
---
{% include JB/setup %}

## Markdown Interpreter for Apache Zeppelin

### Overview
[Markdown](http://daringfireball.net/projects/markdown/) is a plain text formatting syntax designed so that it can be converted to HTML.
Zeppelin uses markdown4j. For more examples and extension support, please checkout [here](https://code.google.com/p/markdown4j/).  
In Zeppelin notebook, you can use ` %md ` in the beginning of a paragraph to invoke the Markdown interpreter and generate static html from Markdown plain text.

In Zeppelin, Markdown interpreter is enabled by default.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-interpreter-setting.png" width="60%" />

### Example
The following example demonstrates the basic usage of Markdown in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-example.png" width="70%" />
