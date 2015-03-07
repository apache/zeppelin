---
layout: page
title: "Writing Zeppelin Interpreter"
description: ""
group: development
---
{% include JB/setup %}

### What is Zeppelin Interpreter

Zeppelin Interpreter is language backend. For example to use scala code in Zeppelin, you need scala interpreter.
Every Interpreter's are belongs to InterpreterGroup. InterpreterGroup is unit of start/stop interpreter.
Interpreters in the same InterpreterGroup can reference each other. For example, SparkSqlInterpreter can refernece SparkInterpreter to get SparkContext from it while they're in the same group. 

<img class="img-responsive" style="width:50%; border: 1px solid #ecf0f1;" height="auto" src="../../assets/themes/zeppelin/img/interpreter.png" />

Interpreter can be launched either using separate classloader or separate JVM process. Sometimes separate classloader cause problem especially when your interpreter uses reflections or trying to grab standard out/err. In this case, separate JVM process is the option you can select. (by checking 'fork' in Interpreter menu, which is default value) When Interpreter is running in separate JVM process, it's communicating with Zeppelin via thrift.

### Make your own Interpreter

Creating a new interpreter is quite simple. Just extends [com.nflabs.zeppelin.interpreter](https://github.com/NFLabs/zeppelin/blob/master/zeppelin-interpreter/src/main/java/com/nflabs/zeppelin/interpreter/Interpreter.java) abstract class and implement some methods.

You can include com.nflabs.zeppelin:zeppelin-interpreter:[VERSION] artifact in your build system.

### Install your interpreter binary

Once you have build your interpreter, you can place your interpreter under directory with all the dependencies.

```
[ZEPPELIN_HOME]/interpreter/[INTERPRETER_NAME]/
```

### Configure your interpreter

You can configure zeppelin.interpreters property in conf/zeppelin-site.xml
Property value is comma separated [INTERPRETER_CLASS_NAME]

for example, 

```
<property>
  <name>zeppelin.interpreters</name>
  <value>com.nflabs.zeppelin.spark.SparkInterpreter,com.nflabs.zeppelin.spark.SparkSqlInterpreter,com.nflabs.zeppelin.markdown.Markdown,com.nflabs.zeppelin.shell.ShellInterpreter,com.me.MyNewInterpreter</value>
</property>
```

### Use your interpreter

Inside of a notebook, %[INTERPRETER_NAME] directive will call your interpreter.
Note that the first interpreter configuration in zeppelin.interpreters will be the default one.

for example

```
%myintp

val a = "My interpreter"
println(a)
```


### Examples

Check some interpreters shipped by default.

 - [spark](https://github.com/NFLabs/zeppelin/tree/master/spark)
 - [markdown](https://github.com/NFLabs/zeppelin/tree/master/markdown)
 - [shell](https://github.com/NFLabs/zeppelin/tree/master/shell)

