---
layout: page
title: "Apache Zeppelin Release 0.7.1"
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

## Apache Zeppelin Release 0.7.1

The Apache Zeppelin community is pleased to announce the availability of the 0.7.1 release.

The community put significant effort into improving Apache Zeppelin since the last release focusing on restarting interpreter process stability, python interpreter improvement, table/chart rendering bug fix.
24 contributors provided 80+ patches for improvements and bug fixes.
More than 70+ issues have been resolved.

We encourage to [download](../../download.html) the latest release. Feedback through the [mailing lists](../../community.html) is very welcome.

### Improvements
   * Rewrite python interpreter for streaming output and seamless matplotlib support
   * Add user configurable option in UI which enable/disable dynamic form(select, checkbox) run on change

### Fixes
   * z.show() doesn't show dataframe
   * Zombie Interpreter processes
   * Interpreter restart button doesn't work
   * On creation of Bar graph zeppelin UI shows it as minigraph
   * Jdbc interpreter sometime doesn't show detailed error message
   * Piechart won't render when column selected as 'key' is changed
   * Make use of all grouped data to draw pie chart
   * Propagate interpreter exception to frontend

<br />
You can visit [issue tracker](https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=12339166&projectId=12316221) for full list of issues being resolved.


<br />
### Contributors

This release would not be possible without the following community members' contributions:

Lee moon soo, Jongyoul Lee, 1ambda, Prabhjyot Singh, AhyoungRyu, Jeff Zhang, Mina Lee, soralee, ess_ess, Remilito, astroshim, Khalid Huseynov, Alexander Wenzel, Naman Mishra, Andreas Weise, Randy Gelhausen, Renjith Kamath, Vipul Modi, agura, NohSeho, Anthony Corbacho, Bruno P. Kinoshita, Elek Márton, Guillermo Cabrera

The following people helped verifying this release:

Jeff Zhang, Ahyoung Ryu, CloverHeartsDev, Sora Lee, Jongyoul Lee, DuyHai Doan, Hyung Sung Shim, Mina Lee, Windy Qin, Jun Kim, Anthony Corbacho, Khalid Huseynov, Felix Cheung, moon soo Lee, Prabhjyot Singh
