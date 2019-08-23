---
layout: page
title: "Notebook Actions"
description: "Description of some actions for notebooks"
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

# Revisions comparator

<div id="toc"></div>

Apache Zeppelin allows you to compare revisions of notebook.
To see which paragraphs have been changed, removed or added.
This action becomes available if your notebook has more than one revision.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/revisions-comparator-button.png" height="90%" width="90%"></center>

## How to compare two revisions

For compare two revisions need open dialog of comparator (by click button) and click on any revision in the table.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/revisions-comparator-table.png" height="90%" width="90%"></center>

Or choose two revisions into comboboxes.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/revisions-comparator-comboboxes.png" height="90%" width="90%"></center>

After click on any revision in the table or selecting the second revision will see the result of the comparison.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/revisions-comparator-diff.png" height="90%" width="90%"></center>

## How to read the result of the comparison

Result it is list of paragraphs which was in both revisions. If paragraph was added in second revision ("Head")
then so it will be marked as <i style="color: green">added</i>, if was deleted then it will be marked as
<i style="color: red">deleted</i>. If paragraph exists in both revisions then it marked as <i style="color: orange">there are differences</i>.
To view the comparison click on the section.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/revisions-comparator-paragraph.png" height="90%" width="90%"></center>

Сhanges in the text of the paragraph are highlighted in green and red. Red it is line (block of lines) which was deleted, green it is line (block of lines) which was added).




