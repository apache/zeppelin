---
layout: page
title: "Interpreters"
description: ""
group: manual
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


## Interpreters in Zeppelin
In this section, we will explain about the role of interpreters, interpreters group and interpreter settings in Zeppelin.
The concept of Zeppelin interpreter allows any language/data-processing-backend to be plugged into Zeppelin.
Currently, Zeppelin supports many interpreters such as Scala( with Apache Spark ), Python( with Apache Spark ), SparkSQL, Hive, Markdown, Shell and so on.

<br/>
## What is Zeppelin interpreter?

Zeppelin Interpreter is a plug-in which enables Zeppelin users to use a specific language/data-processing-backend. For example, to use scala code in Zeppelin, you need `%spark` interpreter.

When you click the ```+Create``` button in the interpreter page, the interpreter drop-down list box will show all the available interpreters on your server.

<img src="/assets/themes/zeppelin/img/screenshots/interpreter_create.png">

<br/>
## What is Zeppelin Interpreter Setting?

Zeppelin interpreter setting is the configuration of a given interpreter on Zeppelin server. For example, the properties are required for hive JDBC interpreter to connect to the Hive server.

<img src="/assets/themes/zeppelin/img/screenshots/interpreter_setting.png">

<br/>
## What is Zeppelin Interpreter Group?

Every Interpreter is belonged to an **Interpreter Group**. Interpreter Group is a unit of start/stop interpreter.
By default, every interpreter is belonged to a single group, but the group might contain more interpreters. For example, spark interpreter group is including Spark support, pySpark, 
SparkSQL and the dependency loader.

Technically, Zeppelin interpreters from the same group are running in the same JVM. For more information about this, please checkout [here](../development/writingzeppelininterpreter.md).

Each interpreters is belonged to a single group and registered together. All of their properties are listed in the interpreter setting like below image.
<img src="/assets/themes/zeppelin/img/screenshots/interpreter_setting_spark.png">

<br/>
## Programming Languages for Interpreter

If the interpreter uses a specific programming language ( like Scala, Python, SQL ), it is generally recommended to add a syntax highlighting supported for that to the notebook paragraph editor.  
  
To check out the list of languages supported, see the `mode-*.js` files under `zeppelin-web/bower_components/ace-builds/src-noconflict` or from [github.com/ajaxorg/ace-builds](https://github.com/ajaxorg/ace-builds/tree/master/src-noconflict).  
  
If you want to add a new set of syntax highlighting,  

1. Add the `mode-*.js` file to `zeppelin-web/bower.json` ( when built, `zeppelin-web/src/index.html` will be changed automatically. ).  
2. Add to the list of `editorMode` in `zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js` - it follows the pattern 'ace/mode/x' where x is the name.  
3. Add to the code that checks for `%` prefix and calls `session.setMode(editorMode.x)` in `setParagraphMode` located in `zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js`.  
  

