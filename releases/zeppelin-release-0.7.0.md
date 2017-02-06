---
layout: page
title: "Apache Zeppelin Release 0.7.0"
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

## Apache Zeppelin Release 0.7.0

The Apache Zeppelin community is pleased to announce the availability of the 0.7.0 release.

The community put significant effort into improving Apache Zeppelin since the last release, focusing on multiuser support, pluggable visualization, better interpreter support.
More than 100+ contributors provided 700+ patches for new features, improvements and bug fixes.
More than 480+ issues have been resolved.

We encourage to [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

### Multiuser Support
   * [ZEPPELIN-987](https://issues.apache.org/jira/browse/ZEPPELIN-987) - Secure interpreter setting, credentials and configurations page
   * [ZEPPELIN-1144](https://issues.apache.org/jira/browse/ZEPPELIN-1144) - Zeppelin home page should only list notebooks with read or write permission
   * [ZEPPELIN-1210](https://issues.apache.org/jira/browse/ZEPPELIN-1210) - Run interpreter per user
   * [ZEPPELIN-1320](https://issues.apache.org/jira/browse/ZEPPELIN-1320) - Run zeppelin interpreter process as web front end user - [Interpreter user impersonation](../../docs/0.7.0/manual/userimpersonation.html)
   * [ZEPPELIN-1472](https://issues.apache.org/jira/browse/ZEPPELIN-1472) - Create new LdapRealm based on Apache Knox LdapRealm: Provides LdapRealm Functionality similar to Apache Knox
   * [ZEPPELIN-1586](https://issues.apache.org/jira/browse/ZEPPELIN-1586) - Add security check in Notebook Rest api
   * [ZEPPELIN-1594](https://issues.apache.org/jira/browse/ZEPPELIN-1594) - Support personalized mode
   * [Zeppelin-1611](https://issues.apache.org/jira/browse/ZEPPELIN-1611) - Support PAM (System User) Authentication
   * [ZEPPELIN-1707](https://issues.apache.org/jira/browse/ZEPPELIN-1707) - Pass userName when creating interpreter through thrift
   * [ZEPPELIN-1730](https://issues.apache.org/jira/browse/ZEPPELIN-1730) - impersonate spark interpreter using --proxy-user
   * [ZEPPELIN-1770](https://issues.apache.org/jira/browse/ZEPPELIN-1770) - Restart only the client user's interpreter when restarting interpreter setting

### Visualization
   * [ZEPPELIN-212](https://issues.apache.org/jira/browse/ZEPPELIN-212) - Multiple paragraph results
   * [ZEPPELIN-732](https://issues.apache.org/jira/browse/ZEPPELIN-732) - [Helium Application](../../helium_packages.html)
   * [ZEPPELIN-1371](https://issues.apache.org/jira/browse/ZEPPELIN-1371) - Add text/numeric conversion support to table display

### Backend interpreter support

This release includes new interpreter support for

   * [Beam](../../docs/0.7.0/interpreter/beam.html)
   * [Pig](../../docs/0.7.0/interpreter/pig.html)
   * [Scio](../../docs/0.7.0/interpreter/scio.html)

#### Spark
   * [ZEPPELIN-1643](https://issues.apache.org/jira/browse/ZEPPELIN-1643) - Make spark web UI accessible from interpreters page
   * [ZEPPELIN-1815](https://issues.apache.org/jira/browse/ZEPPELIN-1815) - Support Spark 2.1
   * [ZEPPELIN-1883](https://issues.apache.org/jira/browse/ZEPPELIN-1883) - Can't import spark submitted packages in PySpark

#### Python
   * [ZEPPELIN-1115](https://issues.apache.org/jira/browse/ZEPPELIN-1115) - [interpreter for SQL over DataFrame](../../docs/0.7.0/interpreter/python.html#sql-over-pandas-dataframes)
   * [ZEPPELIN-1318](https://issues.apache.org/jira/browse/ZEPPELIN-1318) - Add support for matplotlib displaying png images in python interpreter
   * [ZEPPELIN-1345](https://issues.apache.org/jira/browse/ZEPPELIN-1345) - Create a custom matplotlib backend that natively supports inline plotting in a python interpreter cell
   * [ZEPPELIN-1655](https://issues.apache.org/jira/browse/ZEPPELIN-1655) - Dynamic forms in Python interpreter do not work
   * [ZEPPELIN-1671](https://issues.apache.org/jira/browse/ZEPPELIN-1671) - [Conda interpreter](../../docs/0.7.0/interpreter/python.html#conda)
   * [ZEPPELIN-1683](https://issues.apache.org/jira/browse/ZEPPELIN-1683) - [Run python process in docker container](../../docs/0.7.0/interpreter/python.html#docker)

#### Markdown
   * [ZEPPELIN-777](https://issues.apache.org/jira/browse/ZEPPELIN-777) - [Math formula support](../../docs/0.7.0/displaysystem/basicdisplaysystem.html#mathematical-expressions)
   * [ZEPPELIN-1387](https://issues.apache.org/jira/browse/ZEPPELIN-1387) - Support table syntax in markdown interpreter
   * [ZEPPELIN-1614](https://issues.apache.org/jira/browse/ZEPPELIN-1614) - Remove markdown4j dep and use [pegdown](../../docs/0.7.0/interpreter/markdown.html#pegdown-parser) by default

#### Livy
   * [ZEPPELIN-1258](https://issues.apache.org/jira/browse/ZEPPELIN-1258) - Add Spark packages support to Livy interpreter
   * [ZEPPELIN-1293](https://issues.apache.org/jira/browse/ZEPPELIN-1293) - Automatically attach or create to a new session
   * [ZEPPELIN-1432](https://issues.apache.org/jira/browse/ZEPPELIN-1432) - Support cancellation of paragraph execution
   * [ZEPPELIN-1609](https://issues.apache.org/jira/browse/ZEPPELIN-1609) - using pyspark(python3) with livy interperter
   * [ZEPPELIN-2006](https://issues.apache.org/jira/browse/ZEPPELIN-2006) - Livy interpreter doesn't work in anonymous mode

#### Flink
   * [ZEPPELIN-1632](https://issues.apache.org/jira/browse/ZEPPELIN-1632) - Add the possibility to cancel flink jobs in local mode

#### Elasticsearch
   * [ZEPPELIN-1537](https://issues.apache.org/jira/browse/ZEPPELIN-1537) - Elasticsearch improvement for results of aggregations
   * [ZEPPELIN-1821](https://issues.apache.org/jira/browse/ZEPPELIN-1821) - Add HTTP client to elasticsearch interpreter    


### Notebook
   * [ZEPPELIN-1152](https://issues.apache.org/jira/browse/ZEPPELIN-1152) - Listing note revision history
   * [ZEPPELIN-1825](https://issues.apache.org/jira/browse/ZEPPELIN-1825) - Use versioned notebook storage by default

### Job Management
   * [ZEPPELIN-531](https://issues.apache.org/jira/browse/ZEPPELIN-531) - Job management

### UI/UX Improvement
   * [ZEPPELIN-707](https://issues.apache.org/jira/browse/ZEPPELIN-707) - Automatically adds %.* of previous paragraph's typing
   * [ZEPPELIN-1061](https://issues.apache.org/jira/browse/ZEPPELIN-1061) - Select default interpreter while creating note
   * [ZEPPELIN-1564](https://issues.apache.org/jira/browse/ZEPPELIN-1564) - Enable note deletion and paragraph output clear from main page
   * [ZEPPELIN-1566](https://issues.apache.org/jira/browse/ZEPPELIN-1566) - Make paragraph editable with double click
   * [ZEPPELIN-1628](https://issues.apache.org/jira/browse/ZEPPELIN-1628) - Enable renaming note from the main page
   * [ZEPPELIN-1629](https://issues.apache.org/jira/browse/ZEPPELIN-1629) - Enable renaming folder from the main page
   * [ZEPPELIN-1736](https://issues.apache.org/jira/browse/ZEPPELIN-1736) - Introduce trash & enable removing folder

### Noteworthy Changes
   * Zeppelin doesn't use `ZEPPELIN_JAVA_OPTS` as default value of `ZEPPELIN_INTP_JAVA_OPTS` and also the same for `ZEPPELIN_MEM`/`ZEPPELIN_INTP_MEM`. If user want to configure the jvm opts of interpreter process, please set `ZEPPELIN_INTP_JAVA_OPTS` and `ZEPPELIN_INTP_MEM` explicitly. If you don't set `ZEPPELIN_INTP_MEM`, Zeppelin will set it to `-Xms1024m -Xmx1024m -XX:MaxPermSize=512m` by default.
   * Mapping from `%jdbc(prefix)` to `%prefix` is no longer available. Instead, you can use ``%[interpreter alias]`` with multiple interpreter setttings on GUI.
   * Usage of `ZEPPELIN_PORT` is not supported in ssl mode. Instead use `ZEPPELIN_SSL_PORT` to configure the ssl port. Value from `ZEPPELIN_PORT` is used only when `ZEPPELIN_SSL` is set to `false`.
   * The support on Spark 1.1.x to 1.3.x is deprecated.
   * Zeppelin uses `pegdown` as the `markdown.parser.type` option for the `%md` interpreter. Rendered markdown might be different from what you expected
   * note.json format has been changed to support multiple types of output in a paragraph. Zeppelin will automatically convert old format to new format. 0.6 or lower version can read new note.json format but output will not be displayed. For the detail, see [ZEPPELIN-212](http://issues.apache.org/jira/browse/ZEPPELIN-212) and [pull request](https://github.com/apache/zeppelin/pull/1658).
   * Note storage layer will utilize `GitNotebookRepo` by default instead of `VFSNotebookRepo` storage layer, which is an extension of latter one with versioning capabilities on top of it.
   * Markdown and angular paragraphs will hide editor automatically after run and user can open editor by double clicking those paragraphs.
   * Select box dynamic form doesn't run on change but on enter after change.

### Known issues
   * [ZEPPELIN-2048](https://issues.apache.org/jira/browse/ZEPPELIN-2048): Can't run first paragraph when personalize mode on

<br />
You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12336544&projectId=12316221) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members' contributions:

Jeff Zhang, Mina Lee, AhyoungRyu, astroshim, Lee moon soo, Prabhjyot Singh, Khalid Huseynov, 1ambda, Jongyoul Lee, CloverHearts, Damien CORNEAU, Anthony Corbacho, Luciano Resende, Alexander Bezzubov, Renjith Kamath, Alex Goodman, cloverhearts, soralee, Kavin, Sangwoo Lee, WeichenXu, rajarajan-g, felizbear, Igor Drozdov, Myoungdo Park, rawkintrevo, Kai Jiang, Jun Kim, karuppayya, Rafal Wojdyla, Prasad Wagle, agura, Kousuke Saruta, fvaleri, Minwoo Kang, mahmoudelgamal, Naveen Subramanian, Paul Bustios, Peilin Yang, Rerngvit Yanggratoke, Mohammad Amin Khashkhashi Moghaddam, Hao Xia, Bruno Bonnin, Philipp, sergey\_sokur, hyonzin, suvam97, Felix Cheung, vensant, Rohit Choudhary, DuyHai DOAN, Beria, Randy Gelhausen, Sangmin Yoon, meenakshisekar, purechoc, zhongjian, Alexander Shoshin, Benoy Antony, Chin Tzulin, Chris Snow, Daniel Jeon, FireArrow, Jan Hentschel, Jesang Yoon, John Trengrove, Joju Rajan, Karup, Kavin Kumar, Kevin Kim, LantaoJin, Liu Xiang, Matthew Penny, Mleekko, Ondřej Krško, Python_Max, Roger Filmyer, Sagar Kulkarni, Shiv Shankar Subudhi, Steven Han, SungjuKwon, Trevor Grant, Vipin Rathor, Vitaly Polonetsky, Yiming Liu, Yunho Maeng, Zak Hassan, ZhangEthan, amir sanjar, baekhoseok, chie8842, doanduyhai, fred777, gdupont, gss2002, hkropp, hyukjinkwon, laesunk, kenshalo, lichenglin, oganizang, passionke, paulbustios, robbins, sadikovi, terrylee, victor.sheng

The following people helped verifying this release:

CloverHearts, Jun Kim, Prabhjyot Singh, Jeff Zhang, Hyung Sung Shim, Ahyoung Ryu, Md. Rezaul Karim, Alexander Bezzubov, Alexander Goodman, Lei Wang, Felix Cheung, DuyHai Doan, Vinay Shukla, Khalid Huseynov, Jongyoul Lee, Sora Lee, Windy Qin, rohit choudhary, moon soo Lee, Andreas Weise, Renjith Kamath, Mina Lee
