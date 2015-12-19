---
layout: page
title: "Interpreter REST API"
description: ""
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

## Zeppelin REST API
 Zeppelin provides several REST API's for interaction and remote activation of zeppelin functionality.
 
 All REST API are available starting with the following endpoint ```http://[zeppelin-server]:[zeppelin-port]/api```
 
 Note that zeppein REST API receive or return JSON objects, it it recommended you install some JSON view such as 
 [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc)
 
 
 If you work with zeppelin and find a need for an additional REST API please [file an issue or send us mail](../../community.html) 

 <br />
### Interpreter REST API list
  
  The role of registered interpreters, settings and interpreters group is described [here](../manual/interpreters.html)
  
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List registered interpreters</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all the registered interpreters available on the server.</td>
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
          "defaultValue": "512m",
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
          "description": "Max number of SparkSQL result to display."
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
   
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List interpreters settings</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all the interpreters settings registered on the server.</td>
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
      ]
    },  
    {
      "id": "2AY6GV7Q3",
      "name": "spark",
      "group": "spark",
      "properties": {
        "spark.cores.max": "",
        "spark.executor.memory": "512m",
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
      ]
    }
  ]
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
   
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Create an interpreter setting</th>
      <th></th>
    </tr>
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
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td> sample JSON input
      </td>
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
  ]
}
        </pre>
      </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
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
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
  
  
<br/>
   
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Update an interpreter setting</th>
      <th></th>
    </tr>
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
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td> sample JSON input
      </td>
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
  ]
}
        </pre>
      </td>
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
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>

  
<br/>
   
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Delete an interpreter setting</th>
      <th></th>
    </tr>
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
      <td> sample JSON response
      </td>
      <td>
        <pre>{"status":"OK"}</pre>
      </td>
    </tr>
  </table>

  
<br/>
   
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Restart an interpreter</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method restart the given interpreter id.</td>
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
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
      <td>
        <pre>{"status":"OK"}</pre>
      </td>
    </tr>
  </table>
