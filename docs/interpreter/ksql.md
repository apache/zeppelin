---
layout: page
title: "KSQL Interpreter for Apache Zeppelin"
description: "SQL is the streaming SQL engine for Apache Kafka and provides an easy-to-use yet powerful interactive SQL interface for stream processing on Kafka."
group: interpreter
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

# KSQL Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[KSQL](https://www.confluent.io/product/ksql/) is the streaming SQL engine for Apache Kafka®. It provides an easy-to-use yet powerful interactive SQL interface for stream processing on Kafka,

## Configuration
<table class="table-configuration">
  <thead>
    <tr>
      <th>Property</th>
      <th>Default</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>ksql.url</td>
      <td>http://localhost:8080</td>
      <td>The KSQL Endpoint base URL</td>
    </tr>
  </tbody>
</table>

N.b. The interpreter supports all the KSQL properties, i.e. `ksql.streams.auto.offset.reset`.

## Using the KSQL Interpreter
In a paragraph, use `%ksql` and start your SQL query in order to start to interact with KSQL.

Following some examples:

```
%ksql
PRINT 'orders';
```

![PRINT image]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/ksql.1.gif)

```
%ksql
CREATE STREAM ORDERS WITH
  (VALUE_FORMAT='AVRO',
   KAFKA_TOPIC ='orders');
```

![CREATE image]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/ksql.1.gif)

```
%ksql
SELECT *
FROM ORDERS
LIMIT 10
```

![LIMIT image]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/ksql.3.gif)