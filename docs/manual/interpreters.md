---
layout: page
title: "Interpreters in Apache Zeppelin"
description: "This document explains about the role of interpreters, interpreters group and interpreter settings in Apache Zeppelin. The concept of Zeppelin interpreter allows any language/data-processing-backend to be plugged into Zeppelin."
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

# Interpreters in Apache Zeppelin

<div id="toc"></div>

## Overview

In this section, we will explain about the role of interpreters, interpreters group and interpreter settings in Zeppelin.
The concept of Zeppelin interpreter allows any language/data-processing-backend to be plugged into Zeppelin.
Currently, Zeppelin supports many interpreters such as Scala ( with Apache Spark ), Python ( with Apache Spark ), Spark SQL, JDBC, Markdown, Shell and so on.

## What is Zeppelin interpreter?
Zeppelin Interpreter is a plug-in which enables Zeppelin users to use a specific language/data-processing-backend. For example, to use Scala code in Zeppelin, you need `%spark` interpreter.

When you click the ```+Create``` button in the interpreter page, the interpreter drop-down list box will show all the available interpreters on your server.

<img src="../assets/themes/zeppelin/img/screenshots/interpreter_create.png" width="280px">

## What is interpreter setting?
Zeppelin interpreter setting is the configuration of a given interpreter on Zeppelin server. For example, the properties are required for hive JDBC interpreter to connect to the Hive server.

<img src="../assets/themes/zeppelin/img/screenshots/interpreter_setting.png" width="500px">

Properties are exported as environment variable when property name is consisted of upper characters, numbers and underscore ([A-Z_0-9]). Otherwise set properties as JVM property.

Each notebook can be bound to multiple Interpreter Settings using setting icon on upper right corner of the notebook.

<img src="../assets/themes/zeppelin/img/screenshots/interpreter_binding.png" width="800px">



## What is interpreter group?
Every Interpreter is belonged to an **Interpreter Group**. Interpreter Group is a unit of start/stop interpreter.
By default, every interpreter is belonged to a single group, but the group might contain more interpreters. For example, Spark interpreter group is including Spark support, pySpark, Spark SQL and the dependency loader.

Technically, Zeppelin interpreters from the same group are running in the same JVM. For more information about this, please checkout [here](../development/writingzeppelininterpreter.html).

Each interpreters is belonged to a single group and registered together. All of their properties are listed in the interpreter setting like below image.

<img src="../assets/themes/zeppelin/img/screenshots/interpreter_setting_spark.png" width="500px">


## Interpreter binding mode

Each Interpreter Setting can choose one of 'shared', 'scoped', 'isolated' interpreter binding mode.
In 'shared' mode, every notebook bound to the Interpreter Setting will share the single Interpreter instance. In 'scoped' mode, each notebook will create new Interpreter instance in the same interpreter process. In 'isolated' mode, each notebook will create new Interpreter process.

<img src="../assets/themes/zeppelin/img/screenshots/interpreter_persession.png" width="400px">


## Connecting to the existing remote interpreter

Zeppelin users can start interpreter thread embedded in their service. This will provide flexibility to user to start interpreter on remote host. To start interpreter along with your service you have to create an instance of ``RemoteInterpreterServer`` and start it as follows:

```
RemoteInterpreterServer interpreter=new RemoteInterpreterServer(3678); 
// Here, 3678 is the port on which interpreter will listen.    
interpreter.start()  

```

The above code will start interpreter thread inside your process. Once the interpreter is started you can configure zeppelin to connect to RemoteInterpreter by checking **Connect to existing process** checkbox and then provide **Host** and **Port** on which interpreter process is listening as shown in the image below:

<img src="../assets/themes/zeppelin/img/screenshots/existing_interpreter.png" width="450px">


## (Experimental) Interpreter Execution Hooks

Zeppelin allows for users to specify additional code to be executed by an interpreter at pre and post-paragraph code execution. This is primarily useful if you need to run the same set of code for all of the paragraphs within your notebook at specific times. Currently, this feature is only available for the spark and pyspark interpreters. To specify your hook code, you may use '`z.registerHook()`. For example, enter the following into one paragraph:

```python
%pyspark
z.registerHook("post_exec", "print 'This code should be executed before the parapgraph code!'")
z.registerHook("pre_exec", "print 'This code should be executed after the paragraph code!'")
```

These calls will not take into effect until the next time you run a paragraph. In another paragraph, enter
```python
%pyspark
print "This code should be entered into the paragraph by the user!"
```

The output should be:
```
This code should be executed before the paragraph code!
This code should be entered into the paragraph by the user!
This code should be executed after the paragraph code!
```

If you ever need to know the hook code, use `z.getHook()`:
```python
%pyspark
print z.getHook("post_exec")
```
```
print 'This code should be executed after the paragraph code!'
```
Any call to `z.registerHook()` will automatically overwrite what was previously registered. To completely unregister a hook event, use `z.unregisterHook(eventCode)`. Currently only `"post_exec"` and `"pre_exec"` are valid event codes for the Zeppelin Hook Registry system.

Finally, the hook registry is internally shared by other interpreters in the same group. This would allow for hook code for one interpreter REPL to be set by another as follows:

```scala
%spark
z.unregisterHook("post_exec", "pyspark")
```
The API is identical for both the spark (scala) and pyspark (python) implementations.

### Caveats
Calls to `z.registerHook("pre_exec", ...)` should be made with care. If there are errors in your specified hook code, this will cause the interpreter REPL to become unable to execute any code pass the pre-execute stage making it impossible for direct calls to `z.unregisterHook()` to take into effect. Current workarounds include calling `z.unregisterHook()` from a different interpreter REPL in the same interpreter group (see above) or manually restarting the interpreter group in the UI. 

