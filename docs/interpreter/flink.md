---
layout: page
title: "Flink Interpreter for Apache Zeppelin"
description: "Apache Flink is an open source platform for distributed stream and batch data processing."
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

# Flink interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Flink](https://flink.apache.org) is an open source platform for distributed stream and batch data processing. Flink’s core is a streaming dataflow engine that provides data distribution, communication, and fault tolerance for distributed computations over data streams. Flink also builds batch processing on top of the streaming engine, overlaying native iteration support, managed memory, and program optimization.

## How to start local Flink cluster, to test the interpreter
Zeppelin comes with pre-configured flink-local interpreter, which starts Flink in a local mode on your machine, so you do not need to install anything.

## How to configure interpreter to point to Flink cluster
At the "Interpreters" menu, you have to create a new Flink interpreter and provide next properties:

<table class="table-configuration">
  <tr>
    <th>property</th>
    <th>value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>host</td>
    <td>local</td>
    <td>host name of running JobManager. 'local' runs flink in local mode (default)</td>
  </tr>
  <tr>
    <td>port</td>
    <td>6123</td>
    <td>port of running JobManager</td>
  </tr>
</table>

For more information about Flink configuration, you can find it [here](https://ci.apache.org/projects/flink/flink-docs-release-1.0/setup/config.html).

## How to test it's working
You can find an example of Flink usage in the Zeppelin Tutorial folder or try the following word count example, by using the [Zeppelin notebook](https://www.zeppelinhub.com/viewer/notebooks/aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL05GTGFicy96ZXBwZWxpbi1ub3RlYm9va3MvbWFzdGVyL25vdGVib29rcy8yQVFFREs1UEMvbm90ZS5qc29u) from Till Rohrmann's presentation [Interactive data analysis with Apache Flink](http://www.slideshare.net/tillrohrmann/data-analysis-49806564) for Apache Flink Meetup.

```
%sh
rm 10.txt.utf-8
wget http://www.gutenberg.org/ebooks/10.txt.utf-8
```
{% highlight scala %}
%flink
case class WordCount(word: String, frequency: Int)
val bible:DataSet[String] = benv.readTextFile("10.txt.utf-8")
val partialCounts: DataSet[WordCount] = bible.flatMap{
    line =>
        """\b\w+\b""".r.findAllIn(line).map(word => WordCount(word, 1))
//        line.split(" ").map(word => WordCount(word, 1))
}
val wordCounts = partialCounts.groupBy("word").reduce{
    (left, right) => WordCount(left.word, left.frequency + right.frequency)
}
val result10 = wordCounts.first(10).collect()
{% endhighlight %}
