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
- with [SparkSQL](http://spark.apache.org/sql/), [PySpark](https://spark.apache.org/docs/latest/api/python/pyspark.html), [SparkR](https://spark.apache.org/docs/latest/sparkr.html)
- inject [SparkContext](https://spark.apache.org/docs/latest/api/java/org/apache/spark/SparkContext.html) and [SQLContext](https://spark.apache.org/docs/latest/sql-programming-guide.html) automatically
- dependencies loading (jars) at runtime using [dependency loader](../interpreter/spark.html#dependencyloading) 
- canceling job and displaying its progress 
- supporting [Spark Cluster Mode](../setup/deployment/spark_cluster_mode.html#apache-zeppelin-on-spark-cluster-mode) for external spark clusters
- supports [different context per user / note](../usage/interpreter/interpreter_binding_mode.html) 
- sharing variables among PySpark, SparkR and Spark through [ZeppelinContext](../interpreter/spark.html#zeppelincontext)
- [Livy Interpreter](../interpreter/livy.html)

<br/>

For the further information about Spark support in Zeppelin, please check 

- [Spark Interpreter](../interpreter/spark.html)



