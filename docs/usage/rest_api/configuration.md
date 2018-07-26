---
layout: page
title: "Apache Zeppelin Configuration REST API"
description: "This page contains Apache Zeppelin Configuration REST API information."
group: usage/rest_api 
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

# Apache Zeppelin Configuration REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).


## Configuration REST API list

### List all key/value pair of configurations
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all key/value pair of configurations on the server.<br/>
       Note: For security reason, some pairs would not be shown.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/configurations/all```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
      <td>
        <pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "zeppelin.war.tempdir": "webapps",
    "zeppelin.notebook.homescreen.hide": "false",
    "zeppelin.interpreter.remoterunner": "bin/interpreter.sh",
    "zeppelin.notebook.s3.user": "user",
    "zeppelin.server.port": "8089",
    "zeppelin.dep.localrepo": "local-repo",
    "zeppelin.ssl.truststore.type": "JKS",
    "zeppelin.ssl.keystore.path": "keystore",
    "zeppelin.notebook.s3.bucket": "zeppelin",
    "zeppelin.server.addr": "0.0.0.0",
    "zeppelin.ssl.client.auth": "false",
    "zeppelin.server.context.path": "/",
    "zeppelin.ssl.keystore.type": "JKS",
    "zeppelin.ssl.truststore.path": "truststore",
    "zeppelin.ssl": "false",
    "zeppelin.notebook.autoInterpreterBinding": "true",
    "zeppelin.notebook.homescreen": "",
    "zeppelin.notebook.storage": "org.apache.zeppelin.notebook.repo.VFSNotebookRepo",
    "zeppelin.interpreter.connect.timeout": "30000",
    "zeppelin.anonymous.allowed": "true",
    "zeppelin.server.allowed.origins":"*",
    "zeppelin.encoding": "UTF-8"
  }
}</pre></td>
    </tr>
  </table>

<br/>

### List all prefix matched key/value pair of configurations
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all prefix matched key/value pair of configurations on the server.<br/>
      Note: For security reason, some pairs would not be shown.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/configurations/prefix/[prefix]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
      <td><pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "zeppelin.ssl.keystore.type": "JKS",
    "zeppelin.ssl.truststore.path": "truststore",
    "zeppelin.ssl.truststore.type": "JKS",
    "zeppelin.ssl.keystore.path": "keystore",
    "zeppelin.ssl": "false",
    "zeppelin.ssl.client.auth": "false"
  }
}</pre>
      </td>
    </tr>
  </table>
