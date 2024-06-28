---
layout: page
title: "Installing Interpreters"
description: "Apache Zeppelin provides Interpreter Installation mechanism for whom downloaded Zeppelin netinst binary package, or just want to install another 3rd party interpreters."
group: usage/interpreter 
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

# Installing Interpreters 

<div id="toc"></div>

Apache Zeppelin provides **Interpreter Installation** mechanism for whom downloaded Zeppelin `netinst` binary package, or just want to install another 3rd party interpreters.

## Community managed interpreters
Apache Zeppelin provides several interpreters as [community managed interpreters](#available-community-managed-interpreters).
If you downloaded `netinst` binary package, you need to install by using below commands.

#### Install all community managed interpreters

```bash
./bin/install-interpreter.sh --all
```

#### Install specific interpreters

```bash
./bin/install-interpreter.sh --name md,shell,jdbc,python
```

You can get full list of community managed interpreters by running

```bash
./bin/install-interpreter.sh --list
```

<br />
Once you have installed interpreters, you need to restart Zeppelin. And then [create interpreter setting](./overview.html#what-is-zeppelin-interpreter) and [bind it with your notebook](./overview.html#what-is-zeppelin-interpreter-setting).


## 3rd party interpreters

You can also install 3rd party interpreters located in the maven repository by using below commands.

#### Install 3rd party interpreters

```bash
./bin/install-interpreter.sh --name interpreter1 --artifact groupId1:artifact1:version1
```

The above command will download maven artifact `groupId1:artifact1:version1` and all of its transitive dependencies into `interpreter/interpreter1` directory.

After restart Zeppelin, then [create interpreter setting](./overview.html#what-is-zeppelin-interpreter) and [bind it with your note](./overview.html#what-is-interpreter-setting).

#### Install multiple 3rd party interpreters at once

```bash
./bin/install-interpreter.sh --name interpreter1,interpreter2 --artifact groupId1:artifact1:version1,groupId2:artifact2:version2
```

`--name` and `--artifact` arguments will recieve comma separated list.

## Available community managed interpreters

You can also find the below community managed interpreter list in `conf/interpreter-list` file.
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Maven Artifact</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>alluxio</td>
    <td>org.apache.zeppelin:zeppelin-alluxio:0.12.0-SNAPSHOT</td>
    <td>Alluxio interpreter</td>
  </tr>
  <tr>
    <td>angular</td>
    <td>org.apache.zeppelin:zeppelin-angular:0.12.0-SNAPSHOT</td>
    <td>HTML and AngularJS view rendering</td>
  </tr>
  <tr>
    <td>bigquery</td>
    <td>org.apache.zeppelin:zeppelin-bigquery:0.12.0-SNAPSHOT</td>
    <td>BigQuery interpreter</td>
  </tr>
  <tr>
    <td>cassandra</td>
    <td>org.apache.zeppelin:zeppelin-cassandra:0.12.0-SNAPSHOT</td>
    <td>Cassandra interpreter</td>
  </tr>
  <tr>
    <td>elasticsearch</td>
    <td>org.apache.zeppelin:zeppelin-elasticsearch:0.12.0-SNAPSHOT</td>
    <td>Elasticsearch interpreter</td>
  </tr>
  <tr>
    <td>file</td>
    <td>org.apache.zeppelin:zeppelin-file:0.12.0-SNAPSHOT</td>
    <td>HDFS file interpreter</td>
  </tr>
  <tr>
    <td>flink</td>
    <td>org.apache.zeppelin:zeppelin-flink:0.12.0-SNAPSHOT</td>
    <td>Flink interpreter</td>
  </tr>
  <tr>
    <td>hbase</td>
    <td>org.apache.zeppelin:zeppelin-hbase:0.12.0-SNAPSHOT</td>
    <td>Hbase interpreter</td>
  </tr>
  <tr>
    <td>groovy</td>
    <td>org.apache.zeppelin:zeppelin-groovy:0.12.0-SNAPSHOT</td>
    <td>Groovy interpreter</td>
  </tr>
  <tr>
    <td>java</td>
    <td>org.apache.zeppelin:zeppelin-java:0.12.0-SNAPSHOT</td>
    <td>Java interpreter</td>
  </tr>
  <tr>
    <td>jdbc</td>
    <td>org.apache.zeppelin:zeppelin-jdbc:0.12.0-SNAPSHOT</td>
    <td>Jdbc interpreter</td>
  </tr>
  <tr>
    <td>livy</td>
    <td>org.apache.zeppelin:zeppelin-livy:0.12.0-SNAPSHOT</td>
    <td>Livy interpreter</td>
  </tr>
  <tr>
    <td>md</td>
    <td>org.apache.zeppelin:zeppelin-markdown:0.12.0-SNAPSHOT</td>
    <td>Markdown support</td>
  </tr>
  <tr>
    <td>neo4j</td>
    <td>org.apache.zeppelin:zeppelin-neo4j:0.12.0-SNAPSHOT</td>
    <td>Neo4j interpreter</td>
  </tr>
  <tr>
    <td>python</td>
    <td>org.apache.zeppelin:zeppelin-python:0.12.0-SNAPSHOT</td>
    <td>Python interpreter</td>
  </tr>
  <tr>
    <td>shell</td>
    <td>org.apache.zeppelin:zeppelin-shell:0.12.0-SNAPSHOT</td>
    <td>Shell command</td>
  </tr>
  <tr>
    <td>sparql</td>
    <td>org.apache.zeppelin:zeppelin-sparql:0.12.0-SNAPSHOT</td>
    <td>Sparql interpreter</td>
  </tr>
</table>
