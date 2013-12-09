---
layout: page
title: "Writing Zeppelin Library"
description: ""
group: development
---
{% include JB/setup %}

### What is Zeppelin Library
Zeppelin library != Logic.
Zeppelin library = Logic + Resources + Visualization

Zeppelin library let zeppelin user easily extend it's feature.
It aims easy writing. Even non-developer can write one quickly.
Also optionally library can impliment it's own visulization for data.

### Zeppelin Archive Network
Zeppelin library can be published and shared through ZAN

#### Basic structure
Library is reside on local-repository or ZAN. Local-repository is 'zan-repo' directory under Zeppelin installation path at default.

```
[LIBRARY_NAME]/zql.erb
               [LIBRARY_NAME]_[RESOURCES]
               web/index.erb
	       web/[WEB_RESOURCES]
```

**\[LIBRARY_NAME\]**

Creating library is creating a directory with \[LIBRARY_NAME\]. That simple.

**zql.erb**

Query template file in ruby erb format. ZContext is accessible through local varialbe 'z'.


**LIBRARY_NAME_RESOURCES**

Any resource files required by zql.erb. These files will be added by 'add FILE' or 'add JAR' before execution.

**web/index.erb**

Index of web in ruby erb template. ZWebContext is accessible through local variable 'z'.

**web/\[WEB_RESOURCES\]**

Any js, css, image files required by web/index.erb.



#### Call Library
Library can be called in Zeppelin GUI or CLI by ZQL syntax.

```
[LIBRARY_NAME](paramName1=paramValue1, paramName2=paramValue2 ...) [argument]
```

Paramter part and argument part are optional.
If you're using Zengine, library can be called using 'L' class.



#### ZContext
z.in - Input table name

z.out - Output table name

z.arg - library argument

z.param(paramName) - get parameter value by parameterName



#### ZWebContext
z.result - Result data of execution