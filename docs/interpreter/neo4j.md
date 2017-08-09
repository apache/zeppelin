---
layout: page
title: "Neo4j Interpreter for Apache Zeppelin"
description: "Neo4j is a native graph database, designed to store and process graphs from bottom to top."
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

# Neo4j Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Neo4j](https://neo4j.com/product/) is a native graph database, designed to store and process graphs from bottom to top.


![Neo4j - Interpreter - Video]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/neo4j-interpreter-video.gif)

## Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>neo4j.url</td>
    <td>bolt://localhost:7687</td>
    <td>The Neo4j's BOLT url.</td>
  </tr>
  <tr>
    <td>neo4j.auth.type</td>
    <td>BASIC</td>
    <td>The Neo4j's authentication type (NONE, BASIC).</td>
  </tr>
  <tr>
    <td>neo4j.auth.user</td>
    <td>neo4j</td>
    <td>The Neo4j user name.</td>
  </tr>
  <tr>
    <td>neo4j.auth.password</td>
    <td>neo4j</td>
    <td>The Neo4j user password.</td>
  </tr>
  <tr>
    <td>neo4j.max.concurrency</td>
    <td>50</td>
    <td>Max concurrency call from Zeppelin to Neo4j server.</td>
  </tr>
</table>

<center>
  ![Interpreter configuration]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/neo4j-config.png)
</center>


## Enabling the Neo4j Interpreter
In a notebook, to enable the **Neo4j** interpreter, click the **Gear** icon and select **Neo4j**.

## Using the Neo4j Interpreter
In a paragraph, use `%neo4j` to select the Neo4j interpreter and then input the Cypher commands.
For list of Cypher commands please refer to the official [Cyper Refcard](http://neo4j.com/docs/cypher-refcard/current/)

```bash
%neo4j
//Sample the TrumpWorld dataset
WITH
'https://docs.google.com/spreadsheets/u/1/d/1Z5Vo5pbvxKJ5XpfALZXvCzW26Cl4we3OaN73K9Ae5Ss/export?format=csv&gid=1996904412' AS url
LOAD CSV WITH HEADERS FROM url AS row
RETURN row.`Entity A`, row.`Entity A Type`, row.`Entity B`, row.`Entity B Type`, row.Connection, row.`Source(s)`
LIMIT 10
```

The Neo4j interpreter leverages the [Network display system](../usage/display_system/basic.html#network) allowing to visualize the them directly from the paragraph.


### Write your Cypher queries and navigate your graph

This query:

```bash
%neo4j
MATCH (vp:Person {name:"VLADIMIR PUTIN"}), (dt:Person {name:"DONALD J. TRUMP"})
MATCH path = allShortestPaths( (vp)-[*]-(dt) )
RETURN path
```
produces the following result_
![Neo4j - Graph - Result]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/neo4j-graph.png)

### Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form](../usage/dynamic_form/intro.html) inside your queries. This query:

```bash
%neo4j
MATCH (o:Organization)-[r]-()
RETURN o.name, count(*), collect(distinct type(r)) AS types
ORDER BY count(*) DESC
LIMIT ${Show top=10}
```

produces the following result:
![Neo4j - Zeppelin - Dynamic Forms]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/neo4j-dynamic-forms.png)

