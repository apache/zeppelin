---
layout: page
title: "Apache Zeppelin Interpreter REST API"
description: "This page contains Apache Zeppelin Interpreter REST API information."
group: rest-api
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

# Apache Zeppelin Interpreter REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

## Interpreter REST API List

The role of registered interpreters, settings and interpreters group are described in [here](../manual/interpreters.html).

### List of registered interpreters

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all the registered interpreters available on the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "md.md": {
      "name": "md",
      "group": "md",
      "className": "org.apache.zeppelin.markdown.Markdown",
      "properties": {},
      "path": "/zeppelin/interpreter/md"
    },
    "spark.spark": {
      "name": "spark",
      "group": "spark",
      "className": "org.apache.zeppelin.spark.SparkInterpreter",
      "properties": {
        "spark.executor.memory": {
          "defaultValue": "1g",
          "description": "Executor memory per worker instance. ex) 512m, 32g"
        },
        "spark.cores.max": {
          "defaultValue": "",
          "description": "Total number of cores to use. Empty value uses all available core."
        },
      },
      "path": "/zeppelin/interpreter/spark"
    },
    "spark.sql": {
      "name": "sql",
      "group": "spark",
      "className": "org.apache.zeppelin.spark.SparkSqlInterpreter",
      "properties": {
        "zeppelin.spark.maxResult": {
          "defaultValue": "1000",
          "description": "Max number of Spark SQL result to display."
        }
      },
      "path": "/zeppelin/interpreter/spark"
    }
  }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### List of registered interpreter settings

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all the interpreters settings registered on the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
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
      "id": "2AYUGP2D5",
      "name": "md",
      "group": "md",
      "properties": {
        "_empty_": ""
      },
      "interpreterGroup": [
        {
          "class": "org.apache.zeppelin.markdown.Markdown",
          "name": "md"
        }
      ],
      "dependencies": []
    },  
    {
      "id": "2AY6GV7Q3",
      "name": "spark",
      "group": "spark",
      "properties": {
        "spark.cores.max": "",
        "spark.executor.memory": "1g",
      },
      "interpreterGroup": [
        {
          "class": "org.apache.zeppelin.spark.SparkInterpreter",
          "name": "spark"
        },
        {
          "class": "org.apache.zeppelin.spark.SparkSqlInterpreter",
          "name": "sql"
        }
      ],
      "dependencies": [
        {
          "groupArtifactVersion": "com.databricks:spark-csv_2.10:1.3.0"
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
### Get a registered interpreter setting by the setting id 

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns a registered interpreter setting on the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting/[setting ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
          400 if such interpreter setting id does not exist <br/>
          500 for any other errors
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
    "id": "2AYW25ANY",
    "name": "Markdown setting name",
    "group": "md",
    "properties": {
      "propname": "propvalue"
    },
    "interpreterGroup": [
      {
        "class": "org.apache.zeppelin.markdown.Markdown",
        "name": "md"
      }
    ],
    "dependencies": [
      {
        "groupArtifactVersion": "groupId:artifactId:version",
        "exclusions": [
          "groupId:artifactId"
        ]
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Create a new interpreter setting  

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method adds a new interpreter setting using a registered interpreter to the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>201</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
          400 if the input json is empty <br/>
          500 for any other errors
      </td>
    </tr>
    <tr>
      <td>Sample JSON input</td>
      <td>
        <pre>
{
  "name": "Markdown setting name",
  "group": "md",
  "properties": {
    "propname": "propvalue"
  },
  "interpreterGroup": [
    {
      "class": "org.apache.zeppelin.markdown.Markdown",
      "name": "md"
    }
  ],
  "dependencies": [
    {
      "groupArtifactVersion": "groupId:artifactId:version",
      "exclusions": [
        "groupId:artifactId"
      ]
    }
  ]
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "CREATED",
  "message": "",
  "body": {
    "id": "2AYW25ANY",
    "name": "Markdown setting name",
    "group": "md",
    "properties": {
      "propname": "propvalue"
    },
    "interpreterGroup": [
      {
        "class": "org.apache.zeppelin.markdown.Markdown",
        "name": "md"
      }
    ],
    "dependencies": [
      {
        "groupArtifactVersion": "groupId:artifactId:version",
        "exclusions": [
          "groupId:artifactId"
        ]
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Update an interpreter setting
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method updates an interpreter setting with new properties.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting/[interpreter ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td>Sample JSON input</td>
      <td>
        <pre>
{
  "name": "Markdown setting name",
  "group": "md",
  "properties": {
    "propname": "Otherpropvalue"
  },
  "interpreterGroup": [
    {
      "class": "org.apache.zeppelin.markdown.Markdown",
      "name": "md"
    }
  ],
  "dependencies": [
    {
      "groupArtifactVersion": "groupId:artifactId:version",
      "exclusions": [
        "groupId:artifactId"
      ]
    }
  ]
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
    "id": "2AYW25ANY",
    "name": "Markdown setting name",
    "group": "md",
    "properties": {
      "propname": "Otherpropvalue"
    },
    "interpreterGroup": [
      {
        "class": "org.apache.zeppelin.markdown.Markdown",
        "name": "md"
      }
    ],
    "dependencies": [
      {
        "groupArtifactVersion": "groupId:artifactId:version",
        "exclusions": [
          "groupId:artifactId"
        ]
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Delete an interpreter setting

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes an given interpreter setting.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting/[interpreter ID]```</td>
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
      <td>Sample JSON response</td>
      <td>
        <code>{"status":"OK"}</code>
      </td>
    </tr>
  </table>


<br/>
### Restart an interpreter

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method restarts the given interpreter id.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/setting/restart/[interpreter ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td>Sample JSON input (Optional)</td>
      <td>
        <pre>
{
  "noteId": "2AVQJVC8N"
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <code>{"status":"OK"}</code>
      </td>
    </tr>
  </table>

<br/>
### Add a new repository for dependency resolving

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method adds new repository.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/repository```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>201</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td>Sample JSON input</td>
      <td>
        <pre>
{
  "id": "securecentral",
  "url": "https://repo1.maven.org/maven2",
  "snapshot": false
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <code>{"status":"OK"}</code>
      </td>
    </tr>
  </table>

<br/>
### Delete a repository for dependency resolving

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method delete repository with given id.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/interpreter/repository/[repository ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td> 500 </td>
    </tr>
  </table>
  
