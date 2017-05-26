---
layout: page
title: "Apache Zeppelin notebook repository REST API"
description: "This page contains Apache Zeppelin notebook repository REST API information."
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

# Apache Zeppelin Notebook Repository API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

## Notebook Repository REST API List

### List all available notebook repositories

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all the available notebook repositories.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook-repositories```</td>
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
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK",
  "message": "",
  "body": [
    {
      "name": "GitNotebookRepo",
      "className": "org.apache.zeppelin.notebook.repo.GitNotebookRepo",
      "settings": [
        {
          "type": "INPUT",
          "value": [],
          "selected": "ZEPPELIN_HOME/zeppelin/notebook/",
          "name": "Notebook Path"
        }
      ]
    }
  ]
}
        </pre>
      </td>
    </tr>
  </table>

<br/>

### Reload a notebook repository

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method triggers reloading and broadcasting of the note list.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook-repositories/reload```</td>
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
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK",
  "message": ""
}
        </pre>
      </td>
    </tr>
  </table>

<br/>

### Update a specific notebook repository

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method updates a specific notebook repository.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook-repositories```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
        404 when the specified notebook repository doesn't exist <br/> 
        406 for invalid payload <br/>
        500 for any other errors
      </td>
    </tr>
    <tr>
      <td>Sample JSON input</td>
      <td>
        <pre>
{
  "name":"org.apache.zeppelin.notebook.repo.GitNotebookRepo",
  "settings":{
    "Notebook Path":"/tmp/notebook/"
  }
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "name": "GitNotebookRepo",
    "className": "org.apache.zeppelin.notebook.repo.GitNotebookRepo",
    "settings": [
      {
        "type": "INPUT",
        "value": [],
        "selected": "/tmp/notebook/",
        "name": "Notebook Path"
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
