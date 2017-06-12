---
layout: page
title: "SQL with Zeppelin"
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

# SQL support in Zeppelin 

<div id="toc"></div>

<br/>

The following guides explain how to use Apache Zeppelin that enables you to write in SQL:

- provides [JDBC Interpreter](../interpreter/jdbc.html) which allows you can connect any JDBC data sources seamlessly
  * [Postgres](../interpreter/jdbc.html#postgres)
  * [MySQL](../interpreter/jdbc.html#mysql) 
  * [MariaDB](../interpreter/jdbc.html#mariadb)
  * [AWS Redshift](../interpreter/jdbc.html#redshift) 
  * [Apache Hive](../interpreter/jdbc.html#hive)
  * [Apache Phoenix](../interpreter/jdbc.html#apache-phoenix) 
  * [Apache Drill](../interpreter/jdbc.html#apache-drill)
  * [Apache Tajo](../interpreter/jdbc.html#apache-tajo)
  * and so on 
- [Spark Interpreter](../interpreter/spark.html) supports [SparkSQL](http://spark.apache.org/sql/)
- [Python Interpreter](../interpreter/python.html) supports [pandasSQL](../interpreter/python.html#sql-over-pandas-dataframes) 
- can create query result including **UI widgets** using [Dynamic Form](../usage/dynamic_form/intro.html)

    ```sql
    %sql 
    select age, count(1) value 
    from bank 
    where age < ${maxAge=30} 
    group by age 
    order by age
    ```

<br/>

For the further information about SQL support in Zeppelin, please check 

- [JDBC Interpreter](../interpreter/jdbc.html)
- [Spark Interpreter](../interpreter/spark.html)
- [Python Interpreter](../interpreter/python.html)
- [IgniteSQL Interpreter](../interpreter/ignite.html#ignite-sql-interpreter) for [Apache Ignite](https://ignite.apache.org/)
- [Kylin Interpreter](../interpreter/kylin.html) for [Apache Kylin](http://kylin.apache.org/)



