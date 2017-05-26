---
layout: page
title: "Apache Zeppelin Server REST API"
description: "This page contains Apache Zeppelin Server REST API information."
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

# Apache Zeppelin Server REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).


## Zeppelin Server REST API list

### Get Zeppelin version
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns Zeppelin version</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/version```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>500</td>
    </tr>
    <tr>
      <td>sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK",
  "message": "Zeppelin version",
  "body": [
    {
      "version": "0.8.0",
      "git-commit-id": "abc0123",
      "git-timestamp": "2017-01-02 03:04:05"
    }
  ]
}
        </pre>
      </td>
    </tr>
  </table>

### Change the log level of Zeppelin Server 
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method is used to update the root logger's log level of the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/log/level/<LOG_LEVEL>```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>406</td>
    </tr>
    <tr>
      <td>sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK"
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>sample error JSON response</td>
      <td>
        <pre>
{
  "status":"NOT_ACCEPTABLE",
  "message":"Please check LOG level specified. Valid values: DEBUG, ERROR, FATAL, INFO, TRACE, WARN"
}
        </pre>
      </td>
    </tr>
  </table>
