---
layout: page
title: "Zeppelin Release 0.5.5-incubating"
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

## Zeppelin Release 0.5.5-incubating

The Apache Zeppelin (incubating) community is pleased to announce the availability of the 0.5.5-incubating release.
The community put sigificant effort into improving Apache Zeppelin since the last release, focusing on having
new backend support, improvements on stability and simplifying the configuratin. More than 60 contributors provided new features,
improvements and verifying release. More than 90 issues has been resolved.

We encourage [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

<br />

### Backend support

This release includes new backend support for

   * Apache Ignite
   * Apache Lens
   * Apache Cassandra
   * Postgresql
   * Apache Geode (Not included in binary package)


<br />
### Spark integration

Until last release, Zeppelin need to be built for specific version of Spark (and Hadoop).
From this release, a single binary package can be used for any Spark (and Hadoop) version without rebuild.

Configuration is simplified to two steps. Regardless of Spark deployment type (Standalone / Yarn / Mesos),

 * `export SPARK_HOME=`  in *conf/zeppelin-env.sh*
 * set `master` property in GUI (interpreter menu)



<br />
### REST Api and Websocket Secutity

Zeppelin REST Api and Websocket server can be secured from Cross Origin Request problem by setting `zeppelin.server.allowed.origins` property in zeppelin-site.xml



<br />
### Improvements

Some notable improvements are

 * [[ZEPPELIN-210]](https://issues.apache.org/jira/browse/ZEPPELIN-210) - User specified notebook as a homescreen
 * [[ZEPPELIN-126]](https://issues.apache.org/jira/browse/ZEPPELIN-126) - create notebook based on existing notebook
 * [[ZEPPELIN-74]](https://issues.apache.org/jira/browse/ZEPPELIN-74) - Change interpreter selection from %[Name] to %[Group].[Name]
 * [[ZEPPELIN-133]](https://issues.apache.org/jira/browse/ZEPPELIN-133) - Ability to sync notebooks from local to other storage systems
 * [[ZEPPELIN-333]](https://issues.apache.org/jira/browse/ZEPPELIN-333) - Add notebook REST API for create, delete and clone operations


You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12316221&version=12333531) for full list of issues being resolved.


<br />
### Contributors

The following developers contributed to this release:

* Alexander Bezzubov - Improvements and Bug fixes in Core, Build system. Improvements in Documentation.
* Andrey Gura - Ignite interpreter. Improvements in Iginite interpreter.
* Antoine Augusti - Improvements in Documentation.
* Christian Tzolov - Geode interpreter. Postgresql interpreter. Improvements and Bug fixes in UI. Bug fixes in Postgresql interpreter. Improvements in Postgresql interpreter and Documentation.
* Corey Huang - Improvements in Core.
* Damien Corneau - New features in UI. Improvements and Bug fixes in UI and Build system.
* Darren Ha - New features in UI and Core.
* DuyHai DOAN - Cassandra interpreter. Improvements in Spark interpreter.
* Eran Witkon - New features in Core. Bug fixes in UI. Improvements in Core and UI.
* Eric Charles - Bug fixes in UI.
* Felix Cheung - New features and Improvements in Spark interpreter, Build system and Documentation. Improvements in UI. Improvements and Bug fixes in UI.
* Felix Cheung - New feature in Spark interpreter. Improvements and Bug fixes in UI, Documentation and Spark interpreter.
* Hyung sung Shim - Improvements in Core.
* Jeff Steinmetz - Improvements in Documentation.
* Joel Zambrano - New features, Improvements and Bug fixes in Core.
* Jon Buffington - Improvments in Cassandra interpreter.
* Jongyoul Lee - Improvements in Core, Build system and Spark interpreter.
* Ivan Vasyliev - New features in Core.
* Karuppayya - New features in Core and UI.
* Kevin (SangWoo) Kim - Improvements in UI.
* Kirill A. Korinsky - New features in UI.
* Khalid Huseynov - New features in Core.
* Lee moon soo - New features in Core, UI and Spark interpreter. Improvements and Bug fixes in Core, UI, Build system and Spark interpreter. Improvments in Documentation, Flink interpreter.
* Luca Rosellini - Improvements in Spark interpreter.
* Madhuka Udantha- New features and Bug fixes in UI. Improvements in Build system.
* Michael Koltsov - Improvements in UI.
* Mina Lee - New features in Core. Improvements and Bug fixes in UI.
* Neville Li - Improvements in UI.
* Peng Cheng - Improvements in UI, Build system and Spark interpreter.
* Prabhjyot Singh - Improvements and Bug fixes in UI.
* Pranav Agarwal - Lens interpreter. Improvements in Lens interpreter.
* Rafal Wojdyla - Improvements in build system.
* Rajat Gupta - Improvements in UI and Core. Bug fixes in Core.
* Rajesh Koilpillai - Improvements in Documentation.
* Randy Gelhausen - Phoenix interpreter. Improvements in UI, Documentation and Build system.
* Renjith Kamath - New features and Improvements in UI. Improvements in Build system.
* Rex Xiong - Improvements in UI.
* Rick Moritz - Improvements in Core.
* Rohit Agarwal - Improvements and Bug fixes in Core.
* Romi Kuntsman - Improvements in Documentation.
* Ryu Ah young - Improvements in UI. Improvements in Documentation.
* Sibao Hong - Bug fixes in Core.
* Sjoerd Mulder - Improvements in UI.
* Till Rohrmann - Improvements in Flink interpreter.
* Tomas Hudik - Improvements in UI and Documentation.
* Victor Manuel - New features in Core. Improvements in UI and Documentation.
* Vinay Shukla - Improvements in Documentation.
* Zeng Linxi - Bug fixes in Core.
* Zhong Jian - Kylin interpreter.
* caofangkun - New features and Improvements in Build system.
* fantazic - Improvments in Documentation.
* george.jw - Improvments in Documentation.
* opsun - Improvements in UI.


The following people helped verifying this release:

* Alexander Bezzubov
* Amos B. Elberg
* Anthony Corbacho
* Damien Corneau
* DuyHai Doan
* Eran Witkon
* Felix Cheung
* Guillaume
* Henry Saputra
* Jeff Steinmetz
* Jonathan Kelly
* Jongyoul Lee
* Justin Mclean
* Khalid Huseynov
* Lee moon soo
* Madhuka Udantha
* Mina Lee
* Paul Curtis
* Pranav Agarwal
* Renjith Kamath
* Rohit Choudhary
* Steve Loughra
* Victor Manuel
* Vinay Shukla
* Zhong Jian

