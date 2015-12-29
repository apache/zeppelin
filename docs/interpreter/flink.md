---
layout: page
title: "Flink Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## Flink interpreter for Apache Zeppelin
[Apache Flink](https://flink.apache.org) is an open source platform for distributed stream and batch data processing.


### How to start local Flink cluster, to test the interpreter
Zeppelin comes with pre-configured flink-local interpreter, which starts Flink in a local mode on your machine, so you do not need to install anything.

### How to configure interpreter to point to Flink cluster
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
  <tr>
    <td>xxx</td>
    <td>yyy</td>
    <td>anything else from [Flink Configuration](https://ci.apache.org/projects/flink/flink-docs-release-0.9/setup/config.html)</td>
  </tr>
</table>
<br />


### How to test it's working

In example, by using the [Zeppelin notebook](https://www.zeppelinhub.com/viewer/notebooks/aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL05GTGFicy96ZXBwZWxpbi1ub3RlYm9va3MvbWFzdGVyL25vdGVib29rcy8yQVFFREs1UEMvbm90ZS5qc29u) is from [Till Rohrmann's presentation](http://www.slideshare.net/tillrohrmann/data-analysis-49806564) "Interactive data analysis with Apache Flink" for Apache Flink Meetup.


```
%sh
rm 10.txt.utf-8
wget http://www.gutenberg.org/ebooks/10.txt.utf-8
```
```
%flink
case class WordCount(word: String, frequency: Int)
val bible:DataSet[String] = env.readTextFile("10.txt.utf-8")
val partialCounts: DataSet[WordCount] = bible.flatMap{
    line =>
        """\b\w+\b""".r.findAllIn(line).map(word => WordCount(word, 1))
//        line.split(" ").map(word => WordCount(word, 1))
}
val wordCounts = partialCounts.groupBy("word").reduce{
    (left, right) => WordCount(left.word, left.frequency + right.frequency)
}
val result10 = wordCounts.first(10).collect()
```
