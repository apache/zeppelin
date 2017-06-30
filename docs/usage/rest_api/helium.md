---
layout: page
title: "Apache Zeppelin Helium REST API"
description: "This page contains Apache Zeppelin Helium REST API information."
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

# Apache Zeppelin Helium REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

## Helium REST API List

### Get all available helium packages

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all the available helium packages in configured registries.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/package```</td>
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
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
<br/>

### Get all enabled helium packages

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all enabled helium packages in configured registries.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/enabledPackage```</td>
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
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
<br/>

### Get single helium package

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns specified helium package information</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/package/[Package Name]```</td>
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
    ]
  }
}
        </pre>
      </td>
    </tr>
  </table>
<br/>

### Suggest Helium package on a paragraph

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns suggested helium package for the paragraph.</td>
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

### Load Helium package on a paragraph

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method loads helium package to target paragraph.</td>
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
      <td>This ```GET``` method returns bundled helium visualization javascript. When refresh=true (optional) is provided, Zeppelin rebuilds bundle. Otherwise, it's provided from cache</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/bundle/load/[Package Name][?refresh=true]```</td>
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
<br/>

### Get visualization display order

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns display order of enabled visualization packages.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/order/visualization```</td>
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
        <code>{"status":"OK","body":["zeppelin_horizontalbar","zeppelin-bubblechart"]}</code>
      </td>
    </tr>
  </table>
<br/>

### Set visualization display order

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method sets visualization packages display order.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/order/visualization```</td>
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
      <td>Sample JSON input</td>
      <td>
        <code>["zeppelin-bubblechart", "zeppelin_horizontalbar"]</code>
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

### Get configuration for all Helium packages

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns configuration for all Helium packages</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/config```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
  </table>
 <br/>
 
 ### Get configuration for specific package
 
   <table class="table-configuration">
     <col width="200">
     <tr>
       <td>Description</td>
       <td>This ```GET``` method returns configuration for the specified package name and artifact</td>
     </tr>
     <tr>
       <td>URL</td>
       <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/config/[Package Name]/[Artifact]```</td>
     </tr>
     <tr>
       <td>Success code</td>
       <td>200</td>
     </tr>
     <tr>
       <td> Fail code</td>
       <td> 500 </td>
     </tr>
   </table>
<br/>

### Set configuration for specific package

  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method updates configuration for specified package name and artifact</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/config/[Package Name]/[Artifact]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
  </table>   
 <br/>
 
 ### Get Spell configuration for single package
 
   <table class="table-configuration">
     <col width="200">
     <tr>
       <td>Description</td>
       <td>This ```GET``` method returns specified package Spell configuration</td>
     </tr>
     <tr>
       <td>URL</td>
       <td>```http://[zeppelin-server]:[zeppelin-port]/api/helium/spell/config/[Package Name]```</td>
     </tr>
     <tr>
       <td>Success code</td>
       <td>200</td>
     </tr>
     <tr>
       <td> Fail code</td>
       <td> 500 </td>
     </tr>
   </table>
 <br/>
 