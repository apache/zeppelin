---
layout: page
title: "Table Display System"
description: ""
group: display
---
{% include JB/setup %}


### Table

If you have data that row seprated by '\n' (newline) and column separated by '\t' (tab) with first row as header row, for example

<img src="../../assets/themes/zeppelin/img/screenshots/display_table.png" />

You can simply use %table display system to leverage Zeppelin's built in visualization.

<img src="../../assets/themes/zeppelin/img/screenshots/display_table1.png" />

Note that display system is backend independent.

If table contents start with %html, it is interpreted as an HTML.

<img src="../../assets/themes/zeppelin/img/screenshots/display_table_html.png" />
