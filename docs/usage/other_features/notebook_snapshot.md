---
layout: page
title: "Notebook Snapshot"
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

# What is Notebook Snapshot?


Notebook snapshot is feature available for Git based Notebook in which user can create a static snapshot on a notebook.
User can create a static view and tag it to any git revision.

<div id="toc"></div>

## How to create a Notebook Snapshot?


### Create a commit at any logical end point.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/first_commit.gif" height="100%" width="100%"></center>

### Create a snapshot with name "dashboard" and tag it to the first commit. Snapshot can be viewed at /notebook/{Notebook ID}/view/{Snapshot Name}

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/create_snapshot.gif" height="100%" width="100%"></center>

### Create a second commit by adding more details.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/second_commit.gif" height="100%" width="100%"></center>

### You can now change the snapshot "dashboard" to second commit. Now the url /notebook/{Notebook ID}/view/{Snapshot Name} will point to 2nd commit.

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/update_snapshot.gif" height="100%" width="100%"></center>
