---
layout: page
title: "Shell Interpreter"
description: "Shell Interpreter"
group: manual
---
{% include JB/setup %}

## Shell interpreter for Apache Zeppelin

### Overview
Shell interpreter uses [Apache Commons Exec](https://commons.apache.org/proper/commons-exec) to execute external processes. 

In Zeppelin notebook, you can use ` %sh ` in the beginning of a paragraph to invoke system shell and run commands.
Note: Currently each command runs as Zeppelin user.

### Example
The following example demonstrates the basic usage of Shell in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/shell-example.png" width="70%" />
