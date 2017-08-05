---
layout: page
title: "Apache Zeppelin Credential REST API"
description: "This page contains Apache Zeppelin Credential REST API information."
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

# Apache Zeppelin Credential REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).

<br />
## Credential REST API List

### List Credential information
  <table class="table-credential">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method returns all key/value pairs of the credential information on the server.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/credential```</td>
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
    "userCredentials":{
      "entity1":{
        "username":"user1",
        "password":"password1"
      },
      "entity2":{
        "username":"user2",
        "password":"password2"
      }
    }
  }
}</pre></td>
    </tr>
  </table>

<br/>
### Create an Credential Information
  <table class="table-credential">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method creates the credential information with new properties.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/credential/```</td>
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
  "entity": "e1",
  "username": "user",
  "password": "password"
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
### Delete all Credential Information

  <table class="table-credential">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes the credential information.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/credential```</td>
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
### Delete an Credential entity

  <table class="table-credential">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes a given credential entity.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/credential/[entity]```</td>
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

