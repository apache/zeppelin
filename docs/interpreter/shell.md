---
layout: page
title: "Shell interpreter for Apache Zeppelin"
description: "Shell interpreter uses Apache Commons Exec to execute external processes."
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

# Shell interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
Zeppelin Shell has two interpreters the default is the %sh interpreter.

### Shell interpreter
Shell interpreter uses [Apache Commons Exec](https://commons.apache.org/proper/commons-exec) to execute external processes. 
In Zeppelin notebook, you can use ` %sh ` in the beginning of a paragraph to invoke system shell and run commands.

### Terminal interpreter
Terminal interpreter uses [hterm](https://chromium.googlesource.com/apps/libapps/+/HEAD/hterm), [Pty4J](https://github.com/JetBrains/pty4j) analog terminal operation.

> **Note :** Currently each command runs as the user Zeppelin server is running as.

## Configuration
At the "Interpreters" menu in Zeppelin dropdown menu, you can set the property value for Shell interpreter.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>shell.command.timeout.millisecs</td>
    <td>60000</td>
    <td>Shell command time out in millisecs</td>
  </tr>
  <tr>
    <td>shell.working.directory.user.home</td>
    <td>false</td>
    <td>If this set to true, the shell's working directory will be set to user home</td>
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
  <tr>
    <td>zeppelin.shell.interpolation</td>
    <td>false</td>
    <td>Enable ZeppelinContext variable interpolation into paragraph text</td>
  </tr>
</table>

## Example

### Shell interpreter
The following example demonstrates the basic usage of Shell in a Zeppelin notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/shell-example.png" />

If you need further information about **Zeppelin Interpreter Setting** for using Shell interpreter, 
please read [What is interpreter setting?](../usage/interpreter/overview.html#what-is-interpreter-setting) section first.

### Kerberos refresh interval
For changing the default behavior of when to renew Kerberos ticket following changes can be made in `conf/zeppelin-env.sh`.

```bash
# Change Kerberos refresh interval (default value is 1d). Allowed postfix are ms, s, m, min, h, and d.
export KERBEROS_REFRESH_INTERVAL=4h
# Change kinit number retries (default value is 5), which means if the kinit command fails for 5 retries consecutively it will close the interpreter. 
export KINIT_FAIL_THRESHOLD=10
```

### Object Interpolation
The shell interpreter also supports interpolation of `ZeppelinContext` objects into the paragraph text.
The following example shows one use of this facility:

####In Scala cell:

```scala
z.put("dataFileName", "members-list-003.parquet")
    // ...
val members = spark.read.parquet(z.get("dataFileName"))
    // ...
```

####In later Shell cell:

```bash
%sh
rm -rf {dataFileName}
```

Object interpolation is disabled by default, and can be enabled (for the Shell interpreter) by
setting the value of the property `zeppelin.shell.interpolation` to `true` (see _Configuration_ above).
More details of this feature can be found in [Zeppelin-Context](../usage/other_features/zeppelin_context.html)

### Terminal interpreter
The following example demonstrates the basic usage of terminal in a Zeppelin notebook.

```bash
%sh.terminal
input any char
```

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/shell-terminal.gif" />
