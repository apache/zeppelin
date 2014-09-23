---
layout: page
title: "Writing Zeppelin Interpreter"
description: ""
group: development
---
{% include JB/setup %}

### What is Zeppelin Interpreter

Zeppelin Interpreter is language backend. For example to use scala code in Zeppelin, you need scala interpreter.


### Make your own Interpreter

Creating a new interpreter is quite simple. Just implementing [com.nflabs.zeppelin.interpreter](https://github.com/NFLabs/zeppelin/blob/master/zeppelin-zengine/src/main/java/com/nflabs/zeppelin/interpreter/Interpreter.java) interface.

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

