---
layout: page
title: "Spark Interpreter Group"
description: ""
group: manual
---
{% include JB/setup %}


## Spark

[Apache Spark](http://spark.apache.org) is supported in Zeppelin with 
Spark Interpreter group, which consisted of 4 interpreters.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%spark</td>
    <td>SparkInterpreter</td>
    <td>Creates SparkContext and provides scala environment</td>
  </tr>
  <tr>
    <td>%pyspark</td>
    <td>PySparkInterpreter</td>
    <td>Provides python environment</td>
  </tr>
  <tr>
    <td>%sql</td>
    <td>SparkSQLInterpreter</td>
    <td>Provides SQL environment</td>
  </tr>
  <tr>
    <td>%dep</td>
    <td>DepInterpreter</td>
    <td>Dependency loader</td>
  </tr>
</table>


<br />


### SparkContext, SQLContext, ZeppelinContext

SparkContext, SQLContext, ZeppelinContext are automatically created and exposed as variable names 'sc', 'sqlContext' and 'z', respectively, both in scala and python environments.

Note that scala / python environment shares the same SparkContext, SQLContext, ZeppelinContext instance.


<a name="dependencyloading"> </a>
<br />
<br />
### Dependency loading

When your code requires external library, instead of doing download/copy/restart Zeppelin, you can eaily do following jobs using %dep interpreter.

 * Load libraries recursively from Maven repository
 * Load libraries from local filesystem
 * Add additional maven repository
 * Automatically add libraries to SparkCluster (You can turn off)

Dep interpreter leverages scala environment. So you can write any scala code here.

Here's usages.

```scala
%dep
z.reset() // clean up previously added artifact and repository

// add maven repository
z.addRepo("RepoName").url("RepoURL")

// add maven snapshot repository
z.addRepo("RepoName").url("RepoURL").snapshot()

// add artifact from filesystem
z.load("/path/to.jar")

// add artifact from maven repository, with no dependency
z.load("groupId:artifactId:version").excludeAll()

// add artifact recursively
z.load("groupId:artifactId:version")

// add artifact recursively except comma separated GroupID:ArtifactId list
z.load("groupId:artifactId:version").exclude("groupId:artifactId,groupId:artifactId, ...")

// exclude with pattern
z.load("groupId:artifactId:version").exclude(*)
z.load("groupId:artifactId:version").exclude("groupId:artifactId:*")
z.load("groupId:artifactId:version").exclude("groupId:*")

// local() skips adding artifact to spark clusters (skipping sc.addJar())
z.load("groupId:artifactId:version").local()
```

Note that %dep interpreter should be used before %spark, %pyspark, %sql.


<a name="zeppelincontext"> </a>
<br />
<br />
### ZeppelinContext


Zeppelin automatically injects ZeppelinContext as variable 'z' in your scala/python environment. ZeppelinContext provides some addtional functions and utility.

<br />
#### Object exchange

ZeppelinContext extends map and it's shared between scala, python environment.
So you can put some object from scala and read it from python, vise versa.

Put object from scala

```scala
%spark
val myObject = ...
z.put("objName", myObject)
```

Get object from python

```python
%python
myObject = z.get("objName")
```

<br />
#### Form creation

ZeppelinContext provides functions for creating forms. 
In scala and python environments, you can create forms programmatically.

```scala
%spark
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

In sql environment, you can create form in simple template.

```
%sql
select * from ${table=defualtTableName} where text like '%${search}%'
```

To learn more about dynamic form, checkout [Dynamic Form](../dynamicform.html).
