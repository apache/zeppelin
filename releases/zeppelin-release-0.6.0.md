---
layout: page
title: "Apache Zeppelin Release 0.6.0"
description: ""
group: release
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

## Apache Zeppelin Release 0.6.0

The Apache Zeppelin community is pleased to announce the availability of the 0.6.0 release.

The community put significant effort into improving Apache Zeppelin since the last release, focusing on having new backend support, implementing authentication and authorization for enterprise.
More than 70+ contributors provided 360+ patches for new features, improvements and bug fixes.
More than 200+ issues have been resolved.

We encourage to [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

### Backend support

This release includes new backend support for

   * [Alluxio](../../docs/0.6.0/interpreter/alluxio.html)
   * [Hbase](../../docs/0.6.0/interpreter/hbase.html)
   * [HDFS](../../docs/0.6.0/interpreter/hdfs.html)
   * [JDBC](../../docs/0.6.0/interpreter/jdbc.html)
   * [Livy](../../docs/0.6.0/interpreter/livy.html)
   * [Python](../../docs/0.6.0/interpreter/python.html)
   * [SparkR](../../docs/0.6.0/interpreter/r.html)

Default backend version has been bumped up as follow:

   * [Cassandra](../../docs/0.6.0/interpreter/cassandra.html): 3.0.1
   * [Elasticsearch](../../docs/0.6.0/interpreter/elasticsearch.html): 2.3.3
   * [Flink](../../docs/0.6.0/interpreter/flink.html): 1.0.3
   * [Ignite](../../docs/0.6.0/interpreter/ignite.html): 1.6.0
   * [Lens](../../docs/0.6.0/interpreter/lens.html): 2.5.0-beta
   * [Spark](../../docs/0.6.0/interpreter/spark.html): 1.6.1

   > Spark 2.0 support planned for 0.6.1 release

Several interpreters are merged into JDBC interpreter. If you were using one of below interpreters, you can continue to use it by setting additional properties and dependencies in JDBC interpreter. Examples of JDBC interprer setting can be found [here](../../docs/0.6.0/interpreter/jdbc.html#examples).

   * Hive
   * Pheonix
   * Tajo

### New features
   * [Shiro Authentication](../../docs/0.6.0/security/shiroauthentication.html)
   * [Notebook Authorization](../../docs/0.6.0/security/notebook_authorization.html)
   * [Azure](../../docs/0.6.0/storage/storage.html#notebook-storage-in-azure), [ZeppelinHub](../../docs/0.6.0/storage/storage.html#storage-in-zeppelinhub) Notebook Storage
   * Paragraph Result Output Streaming
   * Folder Structure Notebook
   * CSV, TSV Download
   * [Configuration Page in GUI](../../docs/0.6.0/quickstart/explorezeppelinui.html#configuration), [Configuration API](../../docs/0.6.0/rest-api/rest-configuration.html)
   * Provide [Version Information](../../docs/0.6.0/quickstart/explorezeppelinui.html#about-zeppelin)

### Improvements
   * Simpler Angular API ([Back-end](../../docs/0.6.0/displaysystem/back-end-angular.html), [Front-end](../../docs/0.6.0/displaysystem/front-end-angular.html))
   * Improve Table Display to Handle Large Data
   * Generalize [Dependency Loading](../../docs/0.6.0/manual/dependencymanagement.html) to All Interpreter
   * Apply New Mechanism to Interpreter Registration
   * Better View on Mobile Device
   * [Dedicated Interpreter Session/Process Per Note](../../docs/0.6.0/manual/interpreters.html#interpreter-binding-mode)
   * Support Alias for JDBC Interpreter
   * [Availability to Connect to Existing Remote Interpreter](../../docs/0.6.0/manual/interpreters.html#connecting-to-the-existing-remote-interpreter)
   * Documentation

### Fixes
   * Import/Clone Notebook Bug
   * OutOfMemory Issue When Run Notebook
   * CDH 5.7.x, MapR 5.1 Support for Spark Interpreter
   * Cron Job Failure on Notebook with Multiple Type of Interpreter

### Noteworthy UI/UX Changes
   * Paragraph Width Shortcut Changed to `Ctrl` + `Shift` + `-/+` for French/German Keyboard
   * Interpreter/Confidential/Configuration Navigation Menu Moved to Right Top [Dropdown Menu](../../docs/0.6.0/quickstart/explorezeppelinui.html#settings)

<br />
You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12332761&styleName=Html&projectId=12316221) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members' contributions:

Lee moon soo, Mina Lee, Prabhjyot Singh, Jongyoul Lee, Ahyoung Ryu, Ravi Ranjan, Luciano Resende, Felix Cheung, DuyHai Doan, Alexander Bezzubov, Khalid Huseynov, Renjith Kamath, Hyung Sung Shim, Damien Corneau, Prasad Wagle, Zhong Wang, Chae-Sung Lim, Minwoo Kang, Eric Charles, Michael Chen, Jeff Steinmetz, Cheng-Yu Hsu, Victor Manuel, Sagar Kulkarni, Jesang Yoon, Chris Matta, Hao Xia, agura, Bruno Bonnin, johnnyws, karuppayya, Sejun Ra, maocorte, Silvio Fiorito, Brent Schneeman, Andrey Oskin, Rohan Ramakrishna, Rohit Choudhary, Rusty Phillips, Andrea, Sachin, Sujen Shah, Tom Runyon, Trevor Grant, Vinay Shukla, Amos Elb, ankur_jain, AllenFang, haden.kim, mahmoudelgamal, michrawson, rerngvit, sadikovi, shijinkui, suvam97, swakrish, wind0727, Federico Valeri, zhangminglei, Frank Rosner, Hayssam Saleh, Hervé Riviere, Jiri Simsa, Johan Muedsam, Jonathan Kelly, Fawad Halim, Jungtaek Lim, Kevin Kim, Kris Geusebroek, Kwon Yeong-Eon, Dr. Stefan Schimanski, Darren Ha, Nate Sammons, Rajat Venkatesh, Ralph Geerkens, Ramu Malur

The following people helped verifying this release:

Alexander Bezzubov, Federico Valeri, DuyHai Doan, Hyung Sung Shim, Victor Manuel Garcia, Prabhjyot Singh, Bruno Bonnin, Anthony Corbacho, Vinay Shukla, Dirceu Semighini Filho, Moon soo Lee, Khalid Huseynov, Ahyoung Ryu, tog, Jongyoul Lee, Felix Cheung, Jeff Steinmetz, Rohit Choudhary, Luciano Resende
