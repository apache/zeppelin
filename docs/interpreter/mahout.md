---
layout: page
title: "Mahout Interpreter for Apache Zeppelin"
description: "Apache Mahout provides a unified API (the R-Like Scala DSL) for quickly creating machine learning algorithms on a variety of engines."
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

# Apache Mahout Interpreter for Apache Zeppelin

<div id="toc"></div>

## Installation

Apache Mahout is a collection of packages that enable machine learning and matrix algebra on underlying engines such as Apache Flink or Apache Spark.  A convenience script for creating and configuring two Mahout enabled interpreters exists.  The `%sparkMahout` and `%flinkMahout` interpreters do not exist by default but can be easily created using this script.  

### Easy Installation
To quickly and easily get up and running using Apache Mahout, run the following command from the top-level directory of the Zeppelin install:
```bash
python scripts/mahout/add_mahout.py
```

This will create the `%sparkMahout` and `%flinkMahout` interpreters, and restart Zeppelin.

### Advanced Installation

The `add_mahout.py` script contains several command line arguments for advanced users.

<table class="table-configuration">
  <tr>
    <th>Argument</th>
    <th>Description</th>
    <th>Example</th>
  </tr>
  <tr>
    <td>--zeppelin_home</td>
    <td>This is the path to the Zeppelin installation.  This flag is not needed if the script is run from the top-level installation directory or from the `zeppelin/scripts/mahout` directory.</td>
    <td>/path/to/zeppelin</td>
  </tr>
  <tr>
    <td>--mahout_home</td>
    <td>If the user has already installed Mahout, this flag can set the path to `MAHOUT_HOME`.  If this is set, downloading Mahout will be skipped.</td>
    <td>/path/to/mahout_home</td>
  </tr>
  <tr>
    <td>--restart_later</td>
    <td>Restarting is necessary for updates to take effect. By default the script will restart Zeppelin for you- restart will be skipped if this flag is set.</td>
    <td>NA</td>
  </tr>
  <tr>
    <td>--force_download</td>
    <td>This flag will force the script to re-download the binary even if it already exists.  This is useful for previously failed downloads.</td>
    <td>NA</td>
  </tr>
  <tr>
      <td>--overwrite_existing</td>
      <td>This flag will force the script to overwrite existing `%sparkMahout` and `%flinkMahout` interpreters. Useful when you want to just start over.</td>
      <td>NA</td>
    </tr>
</table>

__NOTE 1:__ Apache Mahout at this time only supports Spark 1.5 and Spark 1.6 and Scala 2.10.  If the user is using another version of Spark (e.g. 2.0), the `%sparkMahout` will likely not work.  The `%flinkMahout` interpreter will still work and the user is encouraged to develop with that engine as the code can be ported via copy and paste, as is evidenced by the tutorial notebook.

__NOTE 2:__ If using Apache Flink in cluster mode, the following libraries will also need to be coppied to `${FLINK_HOME}/lib`
- mahout-math-0.12.2.jar
- mahout-math-scala_2.10-0.12.2.jar
- mahout-flink_2.10-0.12.2.jar
- mahout-hdfs-0.12.2.jar
- [com.google.guava:guava:14.0.1](http://central.maven.org/maven2/com/google/guava/guava/14.0.1/guava-14.0.1.jar)

## Overview

The [Apache Mahout](http://mahout.apache.org/)™ project's goal is to build an environment for quickly creating scalable performant machine learning applications.

Apache Mahout software provides three major features:

- A simple and extensible programming environment and framework for building scalable algorithms
- A wide variety of premade algorithms for Scala + Apache Spark, H2O, Apache Flink
- Samsara, a vector math experimentation environment with R-like syntax which works at scale

In other words:

*Apache Mahout provides a unified API for quickly creating machine learning algorithms on a variety of engines.*

## How to use

When starting a session with Apache Mahout, depending on which engine you are using (Spark or Flink), a few imports must be made and a _Distributed Context_ must be declared.  Copy and paste the following code and run once to get started.

### Flink

```scala
%flinkMahout

import org.apache.flink.api.scala._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.flinkbindings._
import org.apache.mahout.math._
import scalabindings._
import RLikeOps._

implicit val ctx = new FlinkDistributedContext(benv)
```

### Spark
```scala
%sparkMahout

import org.apache.mahout.math._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.sparkbindings._

implicit val sdc: org.apache.mahout.sparkbindings.SparkDistributedContext = sc2sdc(sc)
```

### Same Code, Different Engines

After importing and setting up the distributed context, the Mahout R-Like DSL is consistent across engines.  The following code will run in both `%flinkMahout` and `%sparkMahout`

```scala
val drmData = drmParallelize(dense(
  (2, 2, 10.5, 10, 29.509541),  // Apple Cinnamon Cheerios
  (1, 2, 12,   12, 18.042851),  // Cap'n'Crunch
  (1, 1, 12,   13, 22.736446),  // Cocoa Puffs
  (2, 1, 11,   13, 32.207582),  // Froot Loops
  (1, 2, 12,   11, 21.871292),  // Honey Graham Ohs
  (2, 1, 16,   8,  36.187559),  // Wheaties Honey Gold
  (6, 2, 17,   1,  50.764999),  // Cheerios
  (3, 2, 13,   7,  40.400208),  // Clusters
  (3, 3, 13,   4,  45.811716)), numPartitions = 2)

drmData.collect(::, 0 until 4)

val drmX = drmData(::, 0 until 4)
val y = drmData.collect(::, 4)
val drmXtX = drmX.t %*% drmX
val drmXty = drmX.t %*% y


val XtX = drmXtX.collect
val Xty = drmXty.collect(::, 0)
val beta = solve(XtX, Xty)
```

## Leveraging Resource Pools and R for Visualization

Resource Pools are a powerful Zeppelin feature that lets us share information between interpreters. A fun trick is to take the output of our work in Mahout and analyze it in other languages.

### Setting up a Resource Pool in Flink

In Spark based interpreters resource pools are accessed via the ZeppelinContext API.  To put and get things from the resource pool one can be done simple
```scala
val myVal = 1
z.put("foo", myVal)
val myFetchedVal = z.get("foo")
```

To add this functionality to a Flink based interpreter we declare the follwoing

```scala
%flinkMahout

import org.apache.zeppelin.interpreter.InterpreterContext

val z = InterpreterContext.get().getResourcePool()
```

Now we can access the resource pool in a consistent manner from the `%flinkMahout` interpreter.


### Passing a variable from Mahout to R and Plotting

In this simple example, we use Mahout (on Flink or Spark, the code is the same) to create a random matrix and then take the Sin of each element. We then randomly sample the matrix and create a tab separated string. Finally we pass that string to R where it is read as a .tsv file, and a DataFrame is created and plotted using native R plotting libraries.

```scala
val mxRnd = Matrices.symmetricUniformView(5000, 2, 1234)
val drmRand = drmParallelize(mxRnd)


val drmSin = drmRand.mapBlock() {case (keys, block) =>  
  val blockB = block.like()
  for (i <- 0 until block.nrow) {
    blockB(i, 0) = block(i, 0)
    blockB(i, 1) = Math.sin((block(i, 0) * 8))
  }
  keys -> blockB
}

z.put("sinDrm", org.apache.mahout.math.drm.drmSampleToTSV(drmSin, 0.85))
```

And then in an R paragraph...

```r
%spark.r {"imageWidth": "400px"}

library("ggplot2")

sinStr = z.get("flinkSinDrm")

data <- read.table(text= sinStr, sep="\t", header=FALSE)

plot(data,  col="red")
```
