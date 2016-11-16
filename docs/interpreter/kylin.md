---
layout: page
title: "Apache Kylin Interpreter for Apache Zeppelin"
description: "Apache Kylin™ is an open source Distributed Analytics Engine designed to provide SQL interface and multi-dimensional analysis (OLAP) on Hadoop supporting extremely large datasets, original contributed from eBay Inc.
."
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

# Apache Kylin Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Kylin](https://kylin.apache.org/) is an open source Distributed Analytics Engine designed to provide SQL interface and multi-dimensional analysis (OLAP) on Hadoop supporting extremely large datasets, original contributed from eBay Inc. The interpreter assumes that Apache Kylin has been installed and you can connect to Apache Kylin from the machine Apache Zeppelin is installed.  
To get start with Apache Kylin, please see [Apache Kylin Quickstart](https://kylin.apache.org/docs15/index.html).

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>kylin.api.url </td>
    <td>http://localhost:7070/kylin/api/query</td>
    <td>kylin query POST API <br/> The format can be like http://&lt;host&gt;:&lt;port&gt;/kylin/api/query</td>
  </tr>
  <tr>
    <td>kylin.api.user</td>
    <td>ADMIN</td>
    <td>kylin user</td>
  </tr>
  <tr>
    <td>kylin.api.password</td>
    <td>KYLIN</td>
    <td>kylin password</td>
  </tr>
  <tr>
    <td>kylin.query.project</td>
    <td>learn_kylin</td>
    <td>String, Project to perform query. Could update at notebook level</td>
  </tr>
  <tr>
    <td>kylin.query.ispartial</td>
    <td>true</td>
    <td>true|false <br/> (@Deprecated since Apache Kylin V1.5) <br/> Whether accept a partial result or not, default be “false”. Set to “false” for production use.</td>
  </tr>
  <tr>
    <td>kylin.query.limit</td>
    <td>5000</td>
    <td>int, Query limit <br/> If limit is set in sql, perPage will be ignored.</td>
  </tr>
  <tr>
    <td>kylin.query.offset</td>
    <td>0</td>
    <td>int, Query offset <br/> If offset is set in sql, curIndex will be ignored.</td>
  </tr>
</table>

## Using the Apache Kylin Interpreter
In a paragraph, use `%kylin(project_name)` to select the **kylin** interpreter, **project name** and then input **sql**. If no project name defined, will use the default project name from the above configuration.

```
%kylin(learn_project)
select count(*) from kylin_sales group by part_dt
```

