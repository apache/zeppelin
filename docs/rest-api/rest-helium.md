---
layout: page
title: "Apache Zeppelin Helium REST API"
description: "This page contains Apache Zeppelin Helium REST API information."
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

# Apache Zeppelin Helium REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

## Helium REST API List

### List of all available helium packages

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all the available helium packages in configured registries.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/all```</td>
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
    "zeppelin.clock": [
      {
        "registry": "local",
        "pkg": {
          "type": "APPLICATION",
          "name": "zeppelin.clock",
          "description": "Clock (example)",
          "artifact": "zeppelin-examples\/zeppelin-example-clock\/target\/zeppelin-example-clock-0.7.0-SNAPSHOT.jar",
          "className": "org.apache.zeppelin.example.app.clock.Clock",
          "resources": [
            [
              ":java.util.Date"
            ]
          ],
          "icon": "icon"
        },
        "enabled": false
      }
    ],
    "zeppelin-bubblechart": [
      {
        "registry": "local",
        "pkg": {
          "type": "VISUALIZATION",
          "name": "zeppelin-bubblechart",
          "description": "Animated bubble chart",
          "artifact": ".\/..\/helium\/zeppelin-bubble",
          "icon": "icon"
        },
        "enabled": true
      },
      {
        "registry": "local",
        "pkg": {
          "type": "VISUALIZATION",
          "name": "zeppelin-bubblechart",
          "description": "Animated bubble chart",
          "artifact": "zeppelin-bubblechart@0.0.2",
          "icon": "icon"
        },
        "enabled": false
      }
    ],
    "zeppelin_horizontalbar": [
      {
        "registry": "local",
        "pkg": {
          "type": "VISUALIZATION",
          "name": "zeppelin_horizontalbar",
          "description": "Horizontal Bar chart (example)",
          "artifact": ".\/zeppelin-examples\/zeppelin-example-horizontalbar",
          "icon": "icon"
        },
        "enabled": true
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Suggest Helium application

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns suggested helium application for the paragraph.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/suggest/[Note ID]/[Paragraph ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
        404 on note or paragraph not exists <br />
        500
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
    "available": [
      {
        "registry": "local",
        "pkg": {
          "type": "APPLICATION",
          "name": "zeppelin.clock",
          "description": "Clock (example)",
          "artifact": "zeppelin-examples\/zeppelin-example-clock\/target\/zeppelin-example-clock-0.7.0-SNAPSHOT.jar",
          "className": "org.apache.zeppelin.example.app.clock.Clock",
          "resources": [
            [
              ":java.util.Date"
            ]
          ],
          "icon": "icon"
        },
        "enabled": true
      }
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
  
<br/>
### Load helium Application on a paragraph

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns a helium Application id on success.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/load/[Note ID]/[Paragraph ID]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
          404 on note or paragraph not exists <br/>
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
  "body": "app_2C5FYRZ1E-20170108-040449_2068241472zeppelin_clock"
}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Load bundled visualization script

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns bundled helium visualization javascript. When refresh=true (optional) is provided, Zeppelin rebuild bundle. otherwise, provided from cache</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/visualizations/load[?refresh=true]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200 reponse body is executable javascript</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>
          200 reponse body is error message string starts with ERROR:<br/>
      </td>
    </tr>
  </table>

<br/>
### Enable package
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method enables a helium package. Needs artifact name in input payload</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/enable/[Package Name]```</td>
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
      <td>Sample input</td>
      <td>
        <pre>
zeppelin-examples/zeppelin-example-clock/target/zeppelin-example-clock-0.7.0-SNAPSHOT.jar
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <pre>
{"status":"OK"}
        </pre>
      </td>
    </tr>
  </table>

<br/>
### Disable package

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method disables a helium package.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/disable/[Package Name]```</td>
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
