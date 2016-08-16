---
layout: page
title: "Download"
description: ""
group: nav-right
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

# Download Apache Zeppelin

The latest release of Apache Zeppelin is **0.6.1**.

  - 0.6.1 released on Aug 15, 2016 ([release notes](./releases/zeppelin-release-0.6.1.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=commit;h=c928f9a46ecacebc868d6dc10a95c02f9018a18e))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.6.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz'">zeppelin-0.6.1.tgz</a>
    ([pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.asc),
     [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.md5),
     [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.sha512))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.6.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz'">zeppelin-0.6.1-bin-all.tgz</a>
    ([pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.asc),
     [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.md5),
     [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.6.1/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.6.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz'">zeppelin-0.6.1-bin-netinst.tgz</a>
    ([pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.asc),
     [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.md5),
     [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.sha512))

    <blockquote style="margin-top: 10px;">
      <p><strong>Note</strong>: Starting version 0.6.1, Zeppelin is built with Scala 2.11 by default. If you want to build Zeppelin with Scala 2.10 or install interpreter built with Scala 2.10, please see <a href='../../docs/0.6.1/install/install.html#2-build-source-with-options' target='_blank'>install</a> or <a href='../../docs/0.6.1/manual/interpreterinstallation.html#install-interpreter-built-with-scala-210' target='_blank'>interpreter installation</a>.</p>
    </blockquote>


## Verify the integrity of the files

It is essential that you [verify](https://www.apache.org/info/verification.html) the integrity of the downloaded files using the PGP or MD5 signatures. This signature should be matched against the [KEYS](https://www.apache.org/dist/zeppelin/KEYS) file.



## Build from source

For developers, to get latest *0.7.0-SNAPSHOT* check [README](https://github.com/apache/zeppelin/blob/master/README.md).



## Old releases

  - 0.6.0 released on Jul 2, 2016 ([release notes](./releases/zeppelin-release-0.6.0.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=commit;h=fa2c0ff93cca49428df8792e7ee35d2b561669bd))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz'">zeppelin-0.6.0.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.sha512))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz'">zeppelin-0.6.0-bin-all.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.6.0/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz'">zeppelin-0.6.0-bin-netinst.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.sha512))

<p />

  - 0.5.6-incubating released on Jan 22, 2016 ([release notes](./releases/zeppelin-release-0.5.6-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.6))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.6-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz'">zeppelin-0.5.6-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.sha512))

    * Binary package:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.6-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz'">zeppelin-0.5.6-incubating-bin-all.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.sha512))

<p />

  - 0.5.5-incubating released on Nov 18, 2015 ([release notes](./releases/zeppelin-release-0.5.5-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.5))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.5-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz'">zeppelin-0.5.5-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.sha512))

    * Binary package:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.5-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz'">zeppelin-0.5.5-incubating-bin-all.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.sha512))

<p />

  - 0.5.0-incubating released on July 23, 2015 ([release notes](./releases/zeppelin-release-0.5.0-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.0))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz'">zeppelin-0.5.0-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.sha))

    * Binary built with spark-1.4.0 and hadoop-2.3:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz'">zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.sha))

    * Binary built with spark-1.3.1 and hadoop-2.3:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz'">zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.sha))

## Available interpreters

Thanks to many Zeppelin contributors, we can provide much more interpreters in every release.
Please check the below table before you download Zeppelin package.

> **Note :** Only Spark interpreter is included in the netinst binary package by default. If you want to use the other interpreters, you need to install them using net-install script.

<table class="table-configuration" style="text-align:center" id="comparing-version">
  <tr>
    <th style="width:10%">Zeppelin</th>
    <th style="width:22%">0.6.1 (latest)</th>
    <th style="width:22%">0.6.0</th>
    <th style="width:22%">0.5.6</th>
    <th style="width:22%">0.5.5</th>
  </tr>
  <tr>
    <td rowspan="2"><a href="http://spark.apache.org/" target="_blank">Spark</a></td>
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
    <td>JDBC</td>
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
    <td><a href="https://cloud.google.com/bigquery/" target="_blank">BigQuery</a></td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="https://www.python.org/" target="_blank">Python</a></td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="http://livy.io/" target="_blank">Livy</a></td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html" target="_blank">HDFS</a></td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="http://www.alluxio.org/" target="_blank">Alluxio</a></td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="https://hbase.apache.org/" target="_blank">Hbase</a></td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td>
        <a href="https://github.com/twitter/scalding" target="_blank">Scalding</a><br/>
        <span style="font-size:75%">Local mode only, not included in binary package</span>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="https://www.elastic.co/products/elasticsearch" target="_blank">Elasticsearch</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>N/A</td>
  </tr>
  <tr>
    <td><a href="https://angularjs.org/" target="_blank">Angular</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://daringfireball.net/projects/markdown/" target="_blank">Markdown</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://commons.apache.org/" target="_blank">Shell</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://flink.apache.org/" target="_blank">Flink</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://hive.apache.org/" target="_blank">Hive</a></td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="http://tajo.apache.org/" target="_blank">Tajo</a></td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="http://cassandra.apache.org/" target="_blank">Cassandra</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td>
        <a href="http://geode.incubator.apache.org/" target="_blank">Geode</a><br/>
        <span style="font-size:75%">Local mode only, not included in binary package</span>
    </td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://ignite.apache.org/" target="_blank">Ignite</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="http://kylin.apache.org/" target="_blank">Kylin</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://lens.apache.org/" target="_blank">Lens</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="https://phoenix.apache.org/" target="_blank">Phoenix</a></td>
    <td>Merged into JDBC interpreter</td>
    <td>Merged into JDBC interpreter</td>
    <td>O</td>
    <td>O</td>
  </tr>
  <tr>
    <td><a href="http://www.postgresql.org/" target="_blank">PostgreSQL</a></td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
    <td>O</td>
  </tr>
</table>

<!--
-------------
### Old release

##### Zeppelin-0.3.3 (2014.03.29)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.3');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.3.tar.gz">zeppelin-0.3.3.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10301))


##### Zeppelin-0.3.2 (2014.03.14)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.2');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.2.tar.gz">zeppelin-0.3.2.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10300))

##### Zeppelin-0.3.1 (2014.03.06)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.1');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.1.tar.gz">zeppelin-0.3.1.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10201))

##### Zeppelin-0.3.0 (2014.02.07)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.0');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.0.tar.gz">zeppelin-0.3.0.tar.gz</a>, ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10200))

##### Zeppelin-0.2.0 (2014.01.22)

Download Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.2.0');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.2.0.tar.gz">zeppelin-0.2.0.tar.gz</a>, ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10001))

-->
