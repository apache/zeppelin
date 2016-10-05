---
layout: page
title: "Scio Interpreter for Apache Zeppelin"
description: "Scio is a Scala DSL for Apache Beam/Google Dataflow model."
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

# Scio Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
Scio is a Scala DSL for [Google Cloud Dataflow](https://github.com/GoogleCloudPlatform/DataflowJavaSDK) and [Apache Beam](http://beam.incubator.apache.org/) inspired by [Spark](http://spark.apache.org/) and [Scalding](https://github.com/twitter/scalding). See the current [wiki](https://github.com/spotify/scio/wiki) and [API documentation](http://spotify.github.io/scio/) for more information.

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.scio.argz</td>
    <td>--runner=InProcessPipelineRunner</td>
    <td>Scio interpreter wide arguments</td>
  </tr>
  <tr>
    <td>zeppelin.scio.maxResult</td>
    <td>1000</td>
    <td>Max number of SCollection results to display</td>
  </tr>

</table>

## Enabling the Scio Interpreter

In a notebook, to enable the **Scio** interpreter, click the **Gear** icon and select **scio**.

## Using the Scio Interpreter

In a paragraph, use `%scio` to select the **Scio** interpreter. You can use it much the same way as vanilla Scala REPL and [Scio REPL](https://github.com/spotify/scio/wiki/Scio-REPL). Context is shared among all *Scio* paragraphs. There is a special variable **argz** which holds arguments from Scio interpreter settings. The easiest way to proceed is to create a context via standard `ContextAndArgs`.

```scala
%scio
val (sc, args) = ContextAndArgs(argz)
```

Use `sc` context the way you would in regular pipeline/REPL.

Example:

```scala
%scio
val (sc, args) = ContextAndArgs(argz)
sc.parallelize(Seq("foo", "foo", "bar")).countByValue.closeAndDisplay()
```

Please refer to [Scio wiki](https://github.com/spotify/scio/wiki) for more complex examples.

### Progress

There can be only one paragraph running at a time. There is no notion of overall progress, thus progress bar will be `0`.

### SCollection display helpers

Scio interpreter comes with display helpers to ease working with Zeppelin notebooks. Simply use `closeAndDisplay()` on `SCollection` to close context and display the results. The number of results is limited by `zeppelin.scio.maxResult` (by default 1000).

Supported `SCollection` types:

 * Scio's typed BigQuery
 * Scala's Products (case classes, tuples)
 * Google BigQuery's TableRow
 * Apache Avro
 * All Scala's `AnyVal`

#### BigQuery example:

```scala
%scio
@BigQueryType.fromQuery("""|SELECT departure_airport,count(case when departure_delay>0 then 1 else 0 end) as no_of_delays
                           |FROM [bigquery-samples:airline_ontime_data.flights]
                           |group by departure_airport
                           |order by 2 desc
                           |limit 10""".stripMargin) class Flights

val (sc, args) = ContextAndArgs(argz)
sc.bigQuerySelect(Flights.query).closeAndDisplay(Flights.schema)
```

#### BigQuery typed example:

```scala
%scio
@BigQueryType.fromQuery("""|SELECT departure_airport,count(case when departure_delay>0 then 1 else 0 end) as no_of_delays
                           |FROM [bigquery-samples:airline_ontime_data.flights]
                           |group by departure_airport
                           |order by 2 desc
                           |limit 10""".stripMargin) class Flights

val (sc, args) = ContextAndArgs(argz)
sc.typedBigQuery[Flights]().flatMap(_.no_of_delays).mean.closeAndDisplay()
```

#### Avro example:

```scala
%scio
import com.spotify.data.ExampleAvro

val (sc, args) = ContextAndArgs(argz)
sc.avroFile[ExampleAvro]("gs://<bucket>/tmp/my.avro").take(10).closeAndDisplay()
```

#### Avro example with a view schema:

```scala
%scio
import com.spotify.data.ExampleAvro
import org.apache.avro.Schema

val (sc, args) = ContextAndArgs(argz)
val view = Schema.parse("""{"type":"record","name":"ExampleAvro","namespace":"com.spotify.data","fields":[{"name":"track","type":"string"}, {"name":"artist", "type":"string"}]}""")

sc.avroFile[EndSongCleaned]("gs://<bucket>/tmp/my.avro").take(10).closeAndDisplay(view)
```

### Google credentials

Scio Interpreter will try to infer your Google Cloud credentials from its environment, it will take into the account:
 * `argz` interpreter settings ([doc](https://github.com/spotify/scio/wiki#options))
 * environment variable (`GOOGLE_APPLICATION_CREDENTIALS`)
 * gcloud configuration

#### BigQuery macro credentials

Currently BigQuery project for macro expansion is inferred using Google Dataflow's [DefaultProjectFactory().create()](https://github.com/GoogleCloudPlatform/DataflowJavaSDK/blob/master/sdk/src/main/java/com/google/cloud/dataflow/sdk/options/GcpOptions.java#L187)
