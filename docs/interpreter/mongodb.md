---
layout: page
title: "MongoDB Interpreter for Apache Zeppelin"
description: "MongoDB is a general purpose, document-based, distributed database built for modern application developers and for the cloud era."
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

# MongoDB interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[MongoDB](https://www.mongodb.com/) is a general purpose, document-based, distributed database built for modern application developers and for the cloud era.
This interpreter use mongo shell to execute [scripts](https://docs.mongodb.com/manual/tutorial/write-scripts-for-the-mongo-shell/)
Use mongo-shell `JavaScript` to analyze data as you need.
## Installing AND Configuration
First, you need to install mongo shell with Zeppelin in the same machine.
If you use mac with brew, follow this instructions.
```
brew tap mongodb/brew
brew install mongodb/brew/mongodb-community-shell
```
Or you can follow this [mongo shell](https://docs.mongodb.com/manual/mongo/)
Second, create mongodb interpreter in Zeppelin.
![MongoDB interpreter install]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/mongo-interpreter-install.png)
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>mongo.shell.path</td>
    <td>mongo</td>
    <td>MongoDB shell local path. <br/> Use `which mongo` to get local path in linux or mac.</td>
  </tr>
  <tr>
     <td>mongo.shell.command.table.limit</td>
     <td>1000</td>
     <td>Limit of documents displayed in a table. <br/> Use table function when get data from mongodb</td>
  </tr>
  <tr>
     <td>mongo.shell.command.timeout</td>
     <td>60000</td>
     <td>MongoDB shell command timeout in millisecond</td>
  </tr>
  <tr>
     <td>mongo.server.host</td>
     <td>localhost</td>
     <td>MongoDB server host to connect to</td>
  </tr>
  <tr>
    <td>mongo.server.port</td>
    <td>27017</td>
    <td>MongoDB server port to connect to</td>
  </tr>
  <tr>
    <td>mongo.server.database</td>
    <td>test</td>
    <td>MongoDB database name</td>
  </tr>
  <tr>
     <td>mongo.server.authentdatabase</td>
     <td></td>
     <td>MongoDB database name for authentication</td>
  </tr>
  <tr>
    <td>mongo.server.username</td>
    <td></td>
    <td>Username for authentication</td>
  </tr>
  <tr>
    <td>mongo.server.password</td>
    <td></td>
    <td>Password for authentication</td>
  </tr>
  <tr>
    <td>mongo.interpreter.concurrency.max</td>
    <td>10</td>
    <td>Max count of scheduler concurrency</td>
  </tr>
</table>
## Examples
The following example demonstrates the basic usage of MongoDB in a Zeppelin notebook.
![MongoDB interpreter examples]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/mongo-examples.png)
Or you can monitor stats of mongodb collections.
![MongoDB interpreter examples]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/mongo-interpreter-monitor.png)

