---
layout: page
title: "InfluxDB Interpreter for Apache Zeppelin"
description: "InfluxDB is an open-source time series database designed to handle high write and query loads."
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

# InfluxDB Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[InfluxDB](https://v2.docs.influxdata.com/v2.0/)  is an open-source time series database (TSDB) developed by InfluxData. It is written in Go and optimized for fast, high-availability storage and retrieval of time series data in fields such as operations monitoring, application metrics, Internet of Things sensor data, and real-time analytics.
This interpreter allows to perform queries in [Flux Language](https://v2.docs.influxdata.com/v2.0/reference/flux/) in Zeppelin Notebook.

### Notes
* This interpreter is compatible with InfluxDB 2.0+ (v2 API, Flux language) 
* Code complete and syntax highlighting is not supported for now

### Example notebook

![InfluxDB notebook]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/influxdb1.png)

### Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>influxdb.url</td>
    <td>http://localhost:9999</td>
    <td>InfluxDB API connection url</td>
  </tr>
  <tr>
    <td>influxdb.org</td>
    <td>my-org</td>
    <td>organization name</td>
  </tr>
  <tr>
    <td>influxdb.token</td>
    <td>my-token</td>
    <td>authorization token for InfluxDB API</td>
  </tr>
  <tr>
    <td>influxdb.logLevel</td>
    <td>NONE</td>
    <td>InfluxDB client library verbosity level (for debugging purpose)</td>
  </tr>
</table>

#### Example configuration

![InfluxDB notebook]({{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/influxdb2.png)

## Overview


## How to use
Basically, you can use

```
%influxdb
from(bucket: "my-bucket")
  |> range(start: -1h)
  |> filter(fn: (r) => r._measurement == "cpu")
  |> filter(fn: (r) => r.cpu == "cpu-total")
  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
```
In this example we use data collected by  `[[inputs.cpu]]` [Telegraf](https://github.com/influxdata/telegraf) input plugin. 

The result of Flux command can contain more one or more tables. In the case of multiple tables, each 
table is rendered as a separate %table structure. This example uses `pivot` 
function to collect values from multiple tables into single table. 

## How to run InfluxDB 2.0 using docker
```bash
docker pull quay.io/influxdb/influxdb:nightly
docker run --name influxdb -p 9999:9999 quay.io/influxdb/influxdb:nightly

## Post onBoarding request, to setup initial user (my-user@my-password), org (my-org) and bucketSetup (my-bucket)"
curl -i -X POST http://localhost:9999/api/v2/setup -H 'accept: application/json' \
    -d '{
            "username": "my-user",
            "password": "my-password",
            "org": "my-org",
            "bucket": "my-bucket",
            "token": "my-token"
        }'

```


 
    

