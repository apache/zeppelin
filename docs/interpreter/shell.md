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


## Properties
You can modify the interpreter configuration in the `Interpreter` section. The most common properties are as follows, but you can specify other properties that need to be connected.

<table class="table-configuration">
  <tr>
    <td>shell.command.timeout.millisecs</td>
    <td>Shell command time out in millisecs. Default = 60000</td>
  </tr>
  <tr>
   <td>zeppelin.shell.auth.type</td>
   <td>Types of authentications' methods supported are SIMPLE, and KERBEROS</td>
  </tr>
  <tr>
   <td>zeppelin.shell.principal</td>
   <td>The principal name to load from the keytab</td>
  </tr>
  <tr>
   <td>zeppelin.shell.keytab.location</td>
   <td>The path to the keytab file</td>
  </tr>
</table>

### Example
The following example demonstrates the basic usage of Shell in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/shell-example.png" width="70%" />
