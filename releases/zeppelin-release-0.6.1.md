---
layout: page
title: "Apache Zeppelin Release 0.6.1"
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

## Apache Zeppelin Release 0.6.1

The Apache Zeppelin community is pleased to announce the availability of the 0.6.1 release.

The community put significant effort into improving Apache Zeppelin since the last release, focusing on supporting Scala 2.11 and Spark 2.0.
22 contributors provided 80+ patches for new features, improvements and bug fixes.
More than 60+ issues have been resolved.

We encourage to [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

### Backend support

This release includes new backend support for

   * [BigQuery](../../docs/0.6.1/interpreter/bigquery.html)

### New Features
   * Scala 2.11 support

### Improvements
   * Spark 2.0 support
   * Direct output print in Python/Pyspark interpreter

### Fixes
   * Render table on second run
   * Show precise paragraph elapsed time
   * Enable Zeppelin to work on Spark in yarn-client mode when authentication is on
   * Fix unintended interpreter properties restoring bug
   * Load notebook with old date format

### Known issues
   * There are two implementation of R/SparkR interpreter in Zeppelin. R interpreter built via `-Pr` is not tested with Spark 2.0. R interpreter users are recommanded to build Zeppelin with `-Psparkr` profile instead.

<br />
You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12336543&styleName=Html&projectId=12316221) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members' contributions:

Mina Lee, Prabhjyot Singh, Jeff Zhang, Lee moon soo, astroshim, CloverHearts, AhyoungRyu, Luciano Resende, Renjith Kamath, Khalid Huseynov, Jongyoul Lee, Sachin, Damien CORNEAU, Sangmin Yoon, Shiv Shankar Subudhi, SungjuKwon, Zak Hassan, ZhangEthan, agura, Karup, Babu Prasad Elumalai, Prasad Wagle

The following people helped verifying this release:

Ahyoung Ryu, Vinay Shukla, Andrey Gura, Jeff Zhang, Sachin Janani, moon soo Lee, Anthony Corbacho, Khalid Huseynov, Hyung Sung Shim, Sourav Mazumder, Prabhjyot Singh, Victor Manuel Garcia, Alexander Bezzubov, Felix Cheung, Mina Lee, Madhuka Udantha, Rohit Choudhary
