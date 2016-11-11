---
layout: page
title: "Apache Zeppelin Properties REST API"
description: "This page contains Apache Zeppelin Properties REST API information."
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

# Apache Zeppelin Properties REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

<br />
## Property REST API List

### List properties 
  <table class="table-property">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all properties(key/value pairs) of the current user</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/property```</td>
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
        "property1":"value1",
        "property2":"value2"
      }
}</pre></td>
    </tr>
  </table>

<br/>
### Create or update a property 
  <table class="table-property">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method creates or updates the property.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/property/```</td>
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
  "name": "property1",
  "value": "value1"
}
        </pre>
      </td>
    </tr>
    <tr>
      <td>Sample JSON response</td>
      <td>
        <pre>
{
  "status": "OK"
}
        </pre>
      </td>
    </tr>
  </table>


<br/>
### Delete all properties
  <table class="table-property">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes the property information.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/property```</td>
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
### Delete a Property

  <table class="table-property">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes a given property.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/property/[name]```</td>
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

