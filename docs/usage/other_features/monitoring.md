---
layout: page
title: "Monitoring"
description: ""
group: usage/other_features
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

# Monitoring & Metrics

 Zeppelin exposes a few usage metrics in the form of JMX Beans (under the org.apache.zeppelin.metrics package) and also as a JSON endpoint (/metrics).

 The current set of metrics is mainly around execution time of common operations - int includes quantiles (p50 and p99), counts and mean.

<div id="toc"></div>

