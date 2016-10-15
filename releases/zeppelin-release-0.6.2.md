---
layout: page
title: "Apache Zeppelin Release 0.6.2"
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

## Apache Zeppelin Release 0.6.2

The Apache Zeppelin community is pleased to announce the availability of the 0.6.2 release.

The community put significant effort into improving Apache Zeppelin since the last release, focusing on better support for Spark 2.0 and bug fixes.
26 contributors provided 40+ patches for improvements and bug fixes.
40 issues have been resolved.

We encourage to [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

### Improvements
   * Note storage aware of user on sync
   * Provide shiro password encryption fucntion using hadoop commons Credential apis and jceks
   * Add new Shiro Realm for ZeppelinHub
   * Notebook versioning in ZeppelinHub Repo 

### Fixes
   * Adding dependency via SPARK\_SUBMIT\_OPTIONS doesn't work with Spark 2.0.0
   * Fix Spark interpreter binary compatibility issue between 2.10 and 2.11
   * Environment variable in interpreter setting doesn't take effect 
   * Fix UDF with Spark 2.0.0 
   * z.show() doesn't work
   * SparkInterpreter doesn't work with Spark2 of HDP 2.5
   * Make %dep work for spark 2.0.0 when SPARK_HOME is not defined
   * Display long interger in abbreviated format on scatterChart/stackedAreaChart
   * The graph legend truncates at the nearest period(.) in its grouping
   * Lamda expressions are not working on CDH 5.7.x Spark

### Known issues
   * There are two implementation of R/SparkR interpreter in Zeppelin. R interpreter built via `-Pr` is not tested with Spark 2.0. R interpreter users are recommanded to build Zeppelin with `-Psparkr` profile instead.

<br />
You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12316221&version=12338135) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members' contributions:

Jeff Zhang, Mina Lee, Kavin, Renjith Kamath, Damien CORNEAU, Lee moon soo, Anthony Corbacho, astroshim, Luciano Resende, CloverHearts, AhyoungRyu, Prabhjyot Singh, rajarajan-g, rawkintrevo, vensant, Daniel Jeon, Jun, Khalid Huseynov, Liu Xiang, Naveen Subramanian, Peilin Yang, Randy Gelhausen, Rohit Choudhary, Steven Han, Vitaly Polonetsky, meenakshisekark

The following people helped verifying this release:

Anthony Corbacho, Jeff Zhang, Ahyoung Ryu, Victor Manuel Garcia, Hyung Sung Shim, Prabhjyot Singh, moon soo Lee, CloverHearts, DuyHai Doan, Felix Cheung, Luciano Resende, rohit choudhary, Mina Lee, shivani firodiya
