---
layout: page
title: "Supported Interpreters"
description: ""
group:
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

# Supported Interpreters

Thanks to many Zeppelin contributors, we can provide much more interpreters in every release.
Please check the below table before you download Zeppelin package.

> **Note :** Only Spark interpreter is included in the netinst binary package by default. If you want to use the other interpreters, you need to install them using net-install script.

<br/>

<table class="table-configuration" style="text-align:center" id="comparing-version">
  <tr>
    <th style="width:10%">Zeppelin</th>
    <th style="width:22%">0.6.1 (latest)</th>
    <th style="width:22%">0.6.0</th>
    <th style="width:22%">0.5.6</th>
    <th style="width:22%">0.5.5</th>
  </tr>
  <tr>
    <td rowspan="2">
      <h6><a href="http://spark.apache.org/" target="_blank">Spark</a><h6>
    </td>
    <td>
        1.1.x, 1.2.x, 1.3.x 1.4.x, 1.5.x, 1.6.x, <strong>2.0.0</strong>
    </td>
    <td>
        1.1.x, 1.2.x, 1.3.x 1.4.x, 1.5.x, 1.6.x
    </td>
    <td rowspan="2">
        1.1.x, 1.2.x, 1.3.x 1.4.x, 1.5.x, 1.6.x
    </td>
    <td rowspan="2">
        1.1.x, 1.2.x, 1.3.x 1.4.x, 1.5.x
    </td>
  </tr>
  <tr> 
    <td>Support Scala 2.11</td>
    <td>SparkR is also available</td>
  </tr>
  <tr>
    <td><h6>JDBC</h6></td>
    <td>
      <a href="http://www.postgresql.org/" target="_blank">PostgreSQL</a>,
      <a href="https://www.mysql.com/" target="_blank">MySQL</a>,
      <a href="https://mariadb.org/" target="_blank">MariaDB</a>,
      <a href="https://aws.amazon.com/documentation/redshift/" target="_blank">Redshift</a>,
      <br/>
      <a href="https://hive.apache.org/" target="_blank">Hive</a>,
      <a href="https://phoenix.apache.org/" target="_blank">Phoenix</a>,
      <a href="https://drill.apache.org/" target="_blank">Drill</a>,
      <a href="http://tajo.apache.org/" target="_blank">Tajo</a> 
      are available
    </td>
    <td>
      <a href="http://www.postgresql.org/" target="_blank">PostgreSQL</a>,
      <a href="https://www.mysql.com/" target="_blank">MySQL</a>,
      <a href="https://mariadb.org/" target="_blank">MariaDB</a>,
      <a href="https://aws.amazon.com/documentation/redshift/" target="_blank">Redshift</a>,
      <br/>
      <a href="https://hive.apache.org/" target="_blank">Hive</a>,
      <a href="https://phoenix.apache.org/" target="_blank">Phoenix</a>,
      <a href="https://drill.apache.org/" target="_blank">Drill</a>,
      <a href="http://tajo.apache.org/" target="_blank">Tajo</a> 
      are available
    </td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://cloud.google.com/bigquery/" target="_blank">BigQuery</a></h6>
    </td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://www.python.org/" target="_blank">Python</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://livy.io/" target="_blank">Livy</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html" target="_blank">HDFS</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://www.alluxio.org/" target="_blank">Alluxio</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://hbase.apache.org/" target="_blank">Hbase</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://github.com/twitter/scalding" target="_blank">Scalding</a></h6>
      <span style="font-size:75%">Local mode only, not included in binary package</span>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://www.elastic.co/products/elasticsearch" target="_blank">Elasticsearch</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://angularjs.org/" target="_blank">Angular</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://code.google.com/archive/p/markdown4j/" target="_blank">Markdown</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://commons.apache.org/" target="_blank">Shell</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://flink.apache.org/" target="_blank">Flink</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://hive.apache.org/" target="_blank">Hive</a></h6>
    </td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://tajo.apache.org/" target="_blank">Tajo</a></h6>
    </td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://cassandra.apache.org/" target="_blank">Cassandra</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://geode.incubator.apache.org/" target="_blank">Geode</a></h6>
      <span style="font-size:75%">Local mode only, not included in binary package</span>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://ignite.apache.org/" target="_blank">Ignite</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://kylin.apache.org/" target="_blank">Kylin</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://lens.apache.org/" target="_blank">Lens</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="https://phoenix.apache.org/" target="_blank">Phoenix</a></h6>
    </td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
      <h6><a href="http://www.postgresql.org/" target="_blank">PostgreSQL</a></h6>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
</table>