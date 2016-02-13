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
Zeppelin uses markdown4j, for more examples and extension support checkout [markdown4j](https://code.google.com/p/markdown4j/)  
In Zeppelin notebook you can use ``` %md ``` in the beginning of a paragraph to invoke the Markdown interpreter to generate static html from Markdown plain text.

In Zeppelin, Markdown interpreter is enabled by default.
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-interpreter-setting.png" width="600px" />

### Example
The following example demonstrates the basic usage of Markdown in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-example.png" width="800px" />
