---
layout: page
title: "HDFS File Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## HDFS File Interpreter for Apache Zeppelin

<br/>
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%hdfs</td>
    <td>HDFSFileInterpreter</td>
    <td>Provides File System commands for HDFS</td>
  </tr>
</table>

<br/>
This interpreter connects to HDFS using the HTTP WebHDFS interface.
It supports the basic shell file commands applied to HDFS, it currently only supports browsing
* You can use <i>ls [PATH]</i> and <i>ls -l [PATH]</i> to list a directory. If the path is missing, then the current directory is listed.
* You can use <i>cd [PATH]</i> to change your current directory by giving a relative or an absolute path.
* You can invoke <i>pwd</i> to see your current directory.

### Create Interpreter 

You can create the HDFS browser by pointing it to the WebHDFS interface of your Hadoop cluster. 

### Configuration
You can modify the configuration of HDFS from the `Interpreter` section.  The HDFS interpreter express the following properties:

 <table class="table-configuration">
   <tr>
     <th>Property Name</th>
     <th>Description</th>
     <th>Default Value</th>
   </tr>
   <tr>
     <td>hdfs.url</td>
     <td>The URL for WebHDFS</td>
     <td>http://localhost:50070/webhdfs/v1/</td>
   </tr>
   <tr>
     <td>hdfs.user</td>
     <td>The WebHDFS user</td>
     <td>hdfs</td>
   </tr>
   <tr>
     <td>hdfs.maxlength</td>
     <td>Maximum number of lines of results fetched</td>
     <td>1000</td>
   </tr>
 </table>
 

#### WebHDFS REST API
You can confirm that you're able to access the WebHDFS API by running a curl command against the WebHDFS end point provided to the interpreter.

Here is an example:
$> curl "http://localhost:50070/webhdfs/v1/?op=LISTSTATUS"