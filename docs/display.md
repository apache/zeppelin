---
layout: page
title: "Display"
description: ""
group: manual
---
{% include JB/setup %}


### Display

Zeppelin prints output of language backend in text, by default.
However, if output contains some magic keyword, Zeppelin automatically formatting the output as Table, Chart, Image, or Html.

<br />
#### Display as Text

If output has no magic keyword provided Zeppelin print the output in text.

<img src="../assets/themes/zeppelin/img/screenshots/display_text.png" />

<br />
#### Display as Html

If output starts with %html, it is interpreted as an html code.

<img src="../assets/themes/zeppelin/img/screenshots/display_html.png" />

<br />
#### Display as Table, Chart

If output starts with %table, it is interpreted as a table. Table can be seen as chart.

Output's format should be, row separated by '\n' (newline) and column separated by '\t' (tab). First row is header.

<img src="../assets/themes/zeppelin/img/screenshots/display_table.png" />

If table contents start with %html, it is interpreted as an HTML.

<img src="../assets/themes/zeppelin/img/screenshots/display_table_html.png" />

<br />
#### Display as Image

If output starts with %img, it is interpreted as base64 encoded image.

<img src="../assets/themes/zeppelin/img/screenshots/display_image.png" />
