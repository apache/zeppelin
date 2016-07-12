---
layout: page
title: "Shell Interpreter"
description: "Shell Interpreter"
group: interpreter
---
{% include JB/setup %}

# Shell interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
Shell interpreter uses [Apache Commons Exec](https://commons.apache.org/proper/commons-exec) to execute external processes. 
In Zeppelin notebook, you can use ` %sh ` in the beginning of a paragraph to invoke system shell and run commands.

> **Note :** Currently each command runs as the user Zeppelin server is running as.

## Configuration
At the "Interpreters" menu in Zeppelin dropdown menu, you can set the property value for Shell interpreter.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>shell.command.timeout.millisecs</td>
    <td>60000</td>
    <td>Shell command time out in millisecs</td>
  </tr>
  <tr>
    <td>zeppelin.shell.auth.type</td>
    <td></td>
    <td>Types of authentications' methods supported are SIMPLE, and KERBEROS</td>
  </tr>
  <tr>
    <td>zeppelin.shell.principal</td>
    <td></td>
    <td>The principal name to load from the keytab</td>
  </tr>
  <tr>
    <td>zeppelin.shell.keytab.location</td>
    <td></td>
    <td>The path to the keytab file</td>
  </tr>
</table>

## Example
The following example demonstrates the basic usage of Shell in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/shell-example.png" />

If you need further information about **Zeppelin Interpreter Setting** for using Shell interpreter, please read [What is interpreter setting?](../manual/interpreters.html#what-is-interpreter-setting) section first.