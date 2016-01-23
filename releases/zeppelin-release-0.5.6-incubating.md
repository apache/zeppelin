---
layout: page
title: "Zeppelin Release 0.5.6-incubating"
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

## Zeppelin Release 0.5.6-incubating

The Apache Zeppelin (incubating) community is pleased to announce the availability of the 0.5.6-incubating release.

The community puts significant effort into improving Apache Zeppelin since the last release, focusing on having new backend support, improvements on stability and simplifying the configuration. 
More than 38 contributors provided new features, improvements and verifying release.
More than 110 issues has been resolved.

We encourage [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

<br />

### Backend support

This release includes new backend support for

   * Spark up to 1.6.0 support
   * Elasticsearch
   * HiveInterpreter (with multiple configurations)
   * Scalding (Local mode only, not included in binary package)


<br />
### Improvements

Some notable improvements are

 - New features (Import/export notebook, read only server, search, Git notebook storage)
 - Improvements (Autoscrolling, direct to new note, auto save on navigation, better REST api, better complition, pyspark and YARN support, improved documentation)
 - Fixes (Cassandra, MapR profile)


You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12316221&version=12334165) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members's contributions:

 * Lee Moon Soo
 * Prabhjyot Singh
 * Alexander Bezzubov
 * Damien CORNEAU
 * Renjith Kamath
 * Mina Lee
 * CloverHearts
 * Jungtaek Lim
 * Ryu Ah young
 * astroshim
 * Felix Cheung
 * Jongyoul Lee
 * Khalid Huseynov
 * Jeff Steinmetz
 * Michael Chen
 * Bruno Bonnin
 * Chae-Sung Lim
 * DuyHai DOAN
 * Eric Charles
 * Estail7s
 * karuppayya
 * Anthony Corbacho
 * Cos
 * eranwitkon
 * JaeHwa Jung
 * Jürgen Thomann
 * Luciano Resende
 * Minwoo Kang
 * Patrick Ethier
 * Paul Curtis
 * Rick Moritz
 * Rohit Agarwal
 * Sriram Krishnan
 * Till Rohrmann
 * tog
 * Tomas
 * Tsuyoshi Miyake
 * Victor


The following people helped verifying this release:

* Alexander Bezzubov
* Jongyoul Lee
* Jesang Yoon
* DuyHai Doan
* Hyung Sung Shim
* Moon soo Lee
* Corneau Damien
* Felix Cheung
* Mina Lee
* Anthony Corbacho
* Jungtaek Lim
* Victor Manuel Garcia
* Jeff Steinmetz
* Jian Zhong
* Khalid Huseynov
* Jian Zhong
* Justin Mclean
* Henry Saputra
* John D. Ament
* Konstantin Boudnik