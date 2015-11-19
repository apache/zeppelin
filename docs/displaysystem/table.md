---
layout: page
title: "Table Display System"
description: ""
group: display
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


### Table

If you have data that row seprated by '\n' (newline) and column separated by '\t' (tab) with first row as header row, for example

<img src="/assets/themes/zeppelin/img/screenshots/display_table.png" />

You can simply use %table display system to leverage Zeppelin's built in visualization.

<img src="/assets/themes/zeppelin/img/screenshots/display_table1.png" />

Note that display system is backend independent.

If table contents start with %html, it is interpreted as an HTML.

<img src="/assets/themes/zeppelin/img/screenshots/display_table_html.png" />
