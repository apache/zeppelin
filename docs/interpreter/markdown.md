---
layout: page
title: "Markdown Interpreter for Apache Zeppelin"
description: "Markdown is a plain text formatting syntax designed so that it can be converted to HTML. Apache Zeppelin uses markdown4j."
group: interpreter
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Markdown Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Markdown](http://daringfireball.net/projects/markdown/) is a plain text formatting syntax designed so that it can be converted to HTML.
Apache Zeppelin uses [pegdown](https://github.com/sirthias/pegdown) and [markdown4j](https://github.com/jdcasey/markdown4j) as markdown parsers.

In Zeppelin notebook, you can use ` %md ` in the beginning of a paragraph to invoke the Markdown interpreter and generate static html from Markdown plain text.

In Zeppelin, Markdown interpreter is enabled by default and uses the [pegdown](https://github.com/sirthias/pegdown) parser.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-interpreter-setting.png" width="60%" />

## Example

The following example demonstrates the basic usage of Markdown in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-example.png" width="70%" />

## Mathematical expression

Markdown interpreter leverages %html display system internally. That means you can mix mathematical expressions with markdown syntax. 
For more information, please see [Mathematical Expression](../usage/display_system/basic.html#mathematical-expressions) section.

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>markdown.parser.type</td>
    <td>pegdown</td>
    <td>Markdown Parser Type. <br/> Available values: pegdown, markdown4j.</td>
  </tr>
</table>


### Pegdown Parser

`pegdown` parser provides github flavored markdown.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-example-pegdown-parser.png" width="70%" />

`pegdown` parser provides [YUML](http://yuml.me/) and [Websequence](https://www.websequencediagrams.com/) plugins also. 

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/markdown-example-pegdown-parser-plugins.png" width="70%" />

### Markdown4j Parser

Since pegdown parser is more accurate and provides much more markdown syntax  
`markdown4j` option might be removed later. But keep this parser for the backward compatibility.


