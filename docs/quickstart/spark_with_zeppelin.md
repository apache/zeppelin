---
layout: page
title: "Spark with Zeppelin"
description: ""
group: quickstart
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

# Spark support in Zeppelin 

<div id="toc"></div>

<br/>

For a brief overview of Apache Spark fundamentals with Apache Zeppelin, see the following guide:

- **built-in** Apache Spark integration.
- With [Spark Scala](https://spark.apache.org/docs/latest/quick-start.html) [SparkSQL](http://spark.apache.org/sql/), [PySpark](https://spark.apache.org/docs/latest/api/python/pyspark.html), [SparkR](https://spark.apache.org/docs/latest/sparkr.html)
- Inject [SparkContext](https://spark.apache.org/docs/latest/api/java/org/apache/spark/SparkContext.html), [SQLContext](https://spark.apache.org/docs/latest/sql-programming-guide.html) and [SparkSession](https://spark.apache.org/docs/latest/sql-programming-guide.html) automatically
- Canceling job and displaying its progress 
- Supports different modes: local, standalone, yarn(client & cluster), k8s
- Dependency management
- Supports [different context per user / note](../usage/interpreter/interpreter_binding_mode.html) 
- Sharing variables among PySpark, SparkR and Spark through [ZeppelinContext](../interpreter/spark.html#zeppelincontext)
- [Livy Interpreter](../interpreter/livy.html)

<br/>

For the further information about Spark support in Zeppelin, please check 

- [Spark Interpreter](../interpreter/spark.html)



