---
layout: page
title: "Writing Zeppelin Interpreter"
description: ""
group: development
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

### What is Zeppelin Interpreter

Zeppelin Interpreter is a language backend. For example to use scala code in Zeppelin, you need scala interpreter.
Every Interpreter belongs to an InterpreterGroup.
Interpreters in the same InterpreterGroup can reference each other. For example, SparkSqlInterpreter can reference SparkInterpreter to get SparkContext from it while they're in the same group.

<img class="img-responsive" style="width:50%; border: 1px solid #ecf0f1;" height="auto" src="/assets/themes/zeppelin/img/interpreter.png" />

InterpreterSetting is configuration of a given InterpreterGroup and a unit of start/stop interpreter.
All Interpreters in the same InterpreterSetting are launched in a single, separate JVM process. The Interpreter communicates with Zeppelin engine via thrift.

In 'Separate Interpreter for each note' mode, new Interpreter instance will be created per notebook. But it still runs on the same JVM while they're in the same InterpreterSettings.


### Make your own Interpreter

Creating a new interpreter is quite simple. Just extend [org.apache.zeppelin.interpreter](https://github.com/apache/incubator-zeppelin/blob/master/zeppelin-interpreter/src/main/java/org/apache/zeppelin/interpreter/Interpreter.java) abstract class and implement some methods.
You can include `org.apache.zeppelin:zeppelin-interpreter:[VERSION]` artifact in your build system.
Your interpreter name is derived from the static register method.

```
static {
    Interpreter.register("MyInterpreterName", MyClassName.class.getName());
  }
```

The name will appear later in the interpreter name option box during the interpreter configuration process.
The name of the interpreter is what you later write to identify a paragraph which should be interpreted using this interpreter.

```
%MyInterpreterName
some interpreter specific code...
```

### Programming Languages for Interpreter
If the interpreter uses a specific programming language ( like Scala, Python, SQL ), it is generally recommended to add a syntax highlighting supported for that to the notebook paragraph editor.  

To check out the list of languages supported, see the `mode-*.js` files under `zeppelin-web/bower_components/ace-builds/src-noconflict` or from [github.com/ajaxorg/ace-builds](https://github.com/ajaxorg/ace-builds/tree/master/src-noconflict).  

If you want to add a new set of syntax highlighting,  

1. Add the `mode-*.js` file to `zeppelin-web/bower.json` ( when built, `zeppelin-web/src/index.html` will be changed automatically. ).  
2. Add to the list of `editorMode` in `zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js` - it follows the pattern 'ace/mode/x' where x is the name.  
3. Add to the code that checks for `%` prefix and calls `session.setMode(editorMode.x)` in `setParagraphMode` located in `zeppelin-web/src/app/notebook/paragraph/paragraph.controller.js`.  

### Install your interpreter binary

Once you have built your interpreter, you can place it under the interpreter directory with all its dependencies.

```
[ZEPPELIN_HOME]/interpreter/[INTERPRETER_NAME]/
```

### Configure your interpreter

To configure your interpreter you need to follow these steps:

1. Add your interpreter class name to the zeppelin.interpreters property in `conf/zeppelin-site.xml`.

  Property value is comma separated [INTERPRETER\_CLASS\_NAME].
  For example,

```
<property>
  <name>zeppelin.interpreters</name>
  <value>org.apache.zeppelin.spark.SparkInterpreter,org.apache.zeppelin.spark.PySparkInterpreter,org.apache.zeppelin.spark.SparkSqlInterpreter,org.apache.zeppelin.spark.DepInterpreter,org.apache.zeppelin.markdown.Markdown,org.apache.zeppelin.shell.ShellInterpreter,org.apache.zeppelin.hive.HiveInterpreter,com.me.MyNewInterpreter</value>
</property>
```

2. Add your interpreter to the [default configuration](https://github.com/apache/incubator-zeppelin/blob/master/zeppelin-zengine/src/main/java/org/apache/zeppelin/conf/ZeppelinConfiguration.java#L397) which is used when there is no `zeppelin-site.xml`.

3. Start Zeppelin by running `./bin/zeppelin-daemon.sh start`.

4. In the interpreter page, click the `+Create` button and configure your interpreter properties.
Now you are done and ready to use your interpreter.

Note that the interpreters released with zeppelin have a [default configuration](https://github.com/apache/incubator-zeppelin/blob/master/zeppelin-zengine/src/main/java/org/apache/zeppelin/conf/ZeppelinConfiguration.java#L397) which is used when there is no `conf/zeppelin-site.xml`.

### Use your interpreter

#### 0.5.0
Inside of a notebook, `%[INTERPRETER_NAME]` directive will call your interpreter.
Note that the first interpreter configuration in zeppelin.interpreters will be the default one.

For example,

```
%myintp

val a = "My interpreter"
println(a)
```

<br />
#### 0.6.0 and later
Inside of a notebook, `%[INTERPRETER_GROUP].[INTERPRETER_NAME]` directive will call your interpreter.
Note that the first interpreter configuration in zeppelin.interpreters will be the default one.

You can omit either [INTERPRETER\_GROUP] or [INTERPRETER\_NAME]. If you omit [INTERPRETER\_NAME], then first available interpreter will be selected in the [INTERPRETER\_GROUP].
Likewise, if you skip [INTERPRETER\_GROUP], then [INTERPRETER\_NAME] will be chosen from default interpreter group.


For example, if you have two interpreter myintp1 and myintp2 in group mygrp, you can call myintp1 like

```
%mygrp.myintp1

codes for myintp1
```

and you can call myintp2 like

```
%mygrp.myintp2

codes for myintp2
```

If you omit your interpreter name, it'll select first available interpreter in the group ( myintp1 ).

```
%mygrp

codes for myintp1

```

You can only omit your interpreter group when your interpreter group is selected as a default group.

```
%myintp2

codes for myintp2
```

### Examples

Checkout some interpreters released with Zeppelin by default.

 - [spark](https://github.com/apache/incubator-zeppelin/tree/master/spark)
 - [markdown](https://github.com/apache/incubator-zeppelin/tree/master/markdown)
 - [shell](https://github.com/apache/incubator-zeppelin/tree/master/shell)
 - [hive](https://github.com/apache/incubator-zeppelin/tree/master/hive)

### Contributing a new Interpreter to Zeppelin releases

We welcome contribution to a new interpreter. Please follow these few steps:

 - First, check out the general contribution guide [here](./howtocontributewebsite.html).
 - Follow the steps in "Make your own Interpreter" section above.
 - Add your interpreter as in the "Configure your interpreter" section above; also add it to the example template [zeppelin-site.xml.template](https://github.com/apache/incubator-zeppelin/blob/master/conf/zeppelin-site.xml.template).
 - Add tests! They are run by Travis for all changes and it is important that they are self-contained.
 - Include your interpreter as a module in [`pom.xml`](https://github.com/apache/incubator-zeppelin/blob/master/pom.xml).
 - Add documentation on how to use your interpreter under `docs/interpreter/`. Follow the Markdown style as this [example](https://github.com/apache/incubator-zeppelin/blob/master/docs/interpreter/elasticsearch.md). Make sure you list config settings and provide working examples on using your interpreter in code boxes in Markdown. Link to images as appropriate (images should go to `docs/assets/themes/zeppelin/img/docs-img/`). And add a link to your documentation in the navigation menu (`docs/_includes/themes/zeppelin/_navigation.html`).
 - Most importantly, ensure licenses of the transitive closure of all dependencies are list in [license file](https://github.com/apache/incubator-zeppelin/blob/master/zeppelin-distribution/src/bin_license/LICENSE).
 - Commit your changes and open a Pull Request on the project [Mirror on GitHub](https://github.com/apache/incubator-zeppelin); check to make sure Travis CI build is passing.
