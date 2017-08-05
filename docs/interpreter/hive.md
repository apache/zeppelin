---
layout: page
title: "Hive Interpreter for Apache Zeppelin"
description: "Apache Hive data warehouse software facilitates querying and managing large datasets residing in distributed storage. Hive provides a mechanism to project structure onto this data and query the data using a SQL-like language called HiveQL. At the same time this language also allows traditional map/reduce programmers to plug in their custom mappers and reducers when it is inconvenient or inefficient to express this logic in HiveQL."
group: interpreter
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

# Hive Interpreter for Apache Zeppelin

<div id="toc"></div>

## Important Notice

Hive Interpreter will be deprecated and merged into JDBC Interpreter. 
You can use Hive Interpreter by using JDBC Interpreter with same functionality. 
See the example below of settings and dependencies.

### Properties
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>hive.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
  </tr>
  <tr>
    <td>hive.url</td>
    <td>jdbc:hive2://localhost:10000</td>
  </tr>
  <tr>
    <td>hive.user</td>
    <td>hiveUser</td>
  </tr>
  <tr>
    <td>hive.password</td>
    <td>hivePassword</td>
  </tr>
</table>

### Dependencies
<table class="table-configuration">
  <tr>
    <th>Artifact</th>
    <th>Exclude</th>
  </tr>
  <tr>
    <td>org.apache.hive:hive-jdbc:0.14.0</td>
    <td></td>
  </tr>
  <tr>
    <td>org.apache.hadoop:hadoop-common:2.6.0</td>
    <td></td>
  </tr>
</table>


### Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>default.driver</td>
    <td>org.apache.hive.jdbc.HiveDriver</td>
    <td>Class path of JDBC driver</td>
  </tr>
  <tr>
    <td>default.url</td>
    <td>jdbc:hive2://localhost:10000</td>
    <td>Url for connection</td>
  </tr>
  <tr>
    <td>default.user</td>
    <td></td>
    <td><b>( Optional ) </b>Username of the connection</td>
  </tr>
  <tr>
    <td>default.password</td>
    <td></td>
    <td><b>( Optional ) </b>Password of the connection</td>
  </tr>
  <tr>
    <td>default.xxx</td>
    <td></td>
    <td><b>( Optional ) </b>Other properties used by the driver</td>
  </tr>
  <tr>
    <td>${prefix}.driver</td>
    <td></td>
    <td>Driver class path of <code>%hive(${prefix})</code> </td>
  </tr>
  <tr>
    <td>${prefix}.url</td>
    <td></td>
    <td>Url of <code>%hive(${prefix})</code> </td>
  </tr>
  <tr>
    <td>${prefix}.user</td>
    <td></td>
    <td><b>( Optional ) </b>Username of the connection of <code>%hive(${prefix})</code> </td>
  </tr>
  <tr>
    <td>${prefix}.password</td>
    <td></td>
    <td><b>( Optional ) </b>Password of the connection of <code>%hive(${prefix})</code> </td>
  </tr>
  <tr>
    <td>${prefix}.xxx</td>
    <td></td>
    <td><b>( Optional ) </b>Other properties used by the driver of <code>%hive(${prefix})</code> </td>
  </tr>
</table>

This interpreter provides multiple configuration with `${prefix}`. User can set a multiple connection properties by this prefix. It can be used like `%hive(${prefix})`.

## Overview

The [Apache Hive](https://hive.apache.org/) ™ data warehouse software facilitates querying and managing large datasets 
residing in distributed storage. Hive provides a mechanism to project structure onto 
this data and query the data using a SQL-like language called HiveQL. 
At the same time this language also allows traditional map/reduce programmers to 
plug in their custom mappers and reducers when it is inconvenient or inefficient to express this logic in HiveQL.

## How to use
Basically, you can use

```sql
%hive
select * from my_table;
```

or

```sql
%hive(etl)
-- 'etl' is a ${prefix}
select * from my_table;
```

You can also run multiple queries up to 10 by default. Changing these settings is not implemented yet.

### Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form](../usage/dynamic_form/intro.html) inside your queries. 
You can use both the `text input` and `select form` parameterization features.

```sql
%hive
SELECT ${group_by}, count(*) as count
FROM retail_demo.order_lineitems_pxf
GROUP BY ${group_by=product_id,product_id|product_name|customer_id|store_id}
ORDER BY count ${order=DESC,DESC|ASC}
LIMIT ${limit=10};
```
