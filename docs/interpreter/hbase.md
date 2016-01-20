---
layout: page
title: "HBase Shell Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## HBase Shell Interpreter for Apache Zeppelin
[HBase Shell](http://hbase.apache.org/book.html#shell) is a JRuby IRB client for Apache HBase. 
This interpreter provides all capabilities of Apache HBase shell within Apache Zeppelin. The 
interpreter assumes that Apache HBase client software has been installed and its possible to 
connect to the Apache HBase cluster from the machine on where Apache Zeppelin is installed.

<br />
## 1. Configuration

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

## 2. Enabling the HBase Shell Interpreter

In a notebook, to enable the **HBase Shell** interpreter, click the **Gear** icon and select 
**HBase Shell**.

## 3. Using the HBase Shell Interpreter

In a paragraph, use `%hbase` to select the **HBase Shell** interpreter and then input all commands.
 To get the list of available commands, use `help`.

```bash
| %hbase
| help
```

For more information on all commands available, refer to [HBase Shell Documentation](http://hbase.apache.org/book.html#shell)