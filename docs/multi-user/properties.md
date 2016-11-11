---
layout: page
title: "User Specific properties in Apache Zeppelin"
description: "Apache Zeppelin allows users to add new properties and these are specific to a user. The user defined properties can be referred in interpreter configuration via place holders."
group:
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

# User specific Properties in Apache Zeppelin

<div id="toc"></div>

## Overview

Apache Zeppelin allows users to add properties and these are specific to a user. The user defined properties can be referred in interpreter configuration via place holders.

For example, let's assume individual users in your organisation need to submit their livy paragraphs to different queues in the Hadoop cluster.
To accomplish this, you will set *livy.spark.yarn.queue* to *${queue, defaultQueue}* as a livy interpreter property.

Now individual users can specify their queues by adding property *queue*. 
If the users do not specify the property *queue*, then *defaultQueue* will be used as the value of *livy.spark.yarn.queue*.

## How to add user specific properties?
You can add new properties in the dropdown menu.

<img class="img-responsive" src="../assets/themes/zeppelin/img/docs-img/properties_tab.png" width="180px"/>

Type **name & value** for the property.


<img class="img-responsive" src="../assets/themes/zeppelin/img/docs-img/add_property.png" />

The properties will be persisted in file **conf/properties.json**  based on the configuration **zeppelin.userproperties.persist**.
