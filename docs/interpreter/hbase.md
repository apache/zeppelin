---
layout: page
title: "HBase Shell Interpreter"
description: ""
group: manual
---
{% include JB/setup %}

## HBase Shell Interpreter for Apache Zeppelin
[HBase Shell](http://hbase.apache.org/book.html#shell) is a JRuby IRB client for Apache HBase. This interpreter provides all capabilities of Apache HBase shell within Apache Zeppelin. The interpreter assumes that Apache HBase client software has been installed and it can connect to the Apache HBase cluster from the machine on where Apache Zeppelin is installed.  
To get start with HBase, please see [HBase Quickstart](https://hbase.apache.org/book.html#quickstart)

> Note: currently only HBase 1.0.x releases are supported.

## Configuration

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>hbase.home</td>
    <td>/usr/lib/hbase</td>
    <td>Installation directory of Hbase</td>
  </tr>
  <tr>
    <td>hbase.ruby.sources</td>
    <td>lib/ruby</td>
    <td>Path to Ruby scripts relative to 'hbase.home'</td>
  </tr>
  <tr>
    <td>hbase.test.mode</td>
    <td>false</td>
    <td>Disable checks for unit and manual tests</td>
  </tr>
</table>

## Enabling the HBase Shell Interpreter

In a notebook, to enable the **HBase Shell** interpreter, click the **Gear** icon and select **HBase Shell**.

## Using the HBase Shell Interpreter

In a paragraph, use `%hbase` to select the **HBase Shell** interpreter and then input all commands. To get the list of available commands, use `help`.

```bash
%hbase
help
```

For example, to create a table

```bash
%hbase
create 'test', 'cf'
```

And then to put data into that table

```bash
%hbase
put 'test', 'row1', 'cf:a', 'value1'
```

For more information on all commands available, refer to [HBase shell commands](https://learnhbase.wordpress.com/2013/03/02/hbase-shell-commands/)
