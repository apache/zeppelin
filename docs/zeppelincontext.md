---
layout: page
title: "ZeppelinContext"
description: ""
group: manual
---
{% include JB/setup %}


### Zeppelin Context

ZeppelinContext is automatically created and injected into Scala language backend.
It provies following function and references.

<br />
#### SparkContext, SQLContext

ZeppelinContext provides reference to SparkContext and SQLContext with some shortcut function.

```scala
/* reference to SparkContext */
z.sc

/* reference to SQLContext */
z.sqlContext

/* Shortcut to z.sqlContext.sql() */
z.sql("select * from ...")
```


<br />
#### Dependency loader (Experimental)

ZeppelinContext provides series of functions that loads jar library from local FS or Remote Maven repository. Loaded library is automatically added into Scala interpreter and SparkContext.

```scala
/* Load a library from local FS */
z.load("/path/to/your.jar")

/* Load a library from Maven repository */
z.load("groupId:artifactId:version")

/* Load library from Maven repository with dependencies */
z.load("groupId:artifactId:version", true)

/* Load a library from Local FS and add it into SparkContext */
z.loadAndDist("/path/to/your.jar")

/* Load a library with dependencies from Maven repository and add it into SparkContext*/
z.loadAndDist("groupId:artifactId:version")

/* Load library with dependencies from maven repository and add it into SparkContext*/
z.loadAndDist("groupId:artifactId:version", true)
```


<br />
#### Form creation

ZeppelinContext also provides functions for creating forms. To learn more about dynamic form, checkout [Dynamic Form](./dynamicform.html).


```scala
/* Create text input form */
z.input("formName")

/* Create text input form with default value */
z.input("formName", "defaultValue")

/* Create select form */
z.select("formName", Seq(("option1", "option1DisplayName"),
                         ("option2", "option2DisplayName")))

/* Create select form with default value*/
z.select("formName", "option1", Seq(("option1", "option1DisplayName"),
                                    ("option2", "option2DisplayName")))
```