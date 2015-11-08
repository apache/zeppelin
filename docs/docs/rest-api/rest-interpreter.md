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
      <td> [Interpreter list sample](rest-json/rest-json-interpreter-list.html)
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
      <td> [Setting list sample](rest-json/rest-json-interpreter-setting.html)
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
      <td> [Create JSON sample](rest-json/rest-json-interpreter-create.html)
      </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
      <td> [Create response sample](rest-json/rest-json-interpreter-create-response.html)
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
      <td> [Update JSON sample](rest-json/rest-json-interpreter-update.html)
      </td>
    </tr>
    <tr>
      <td> sample JSON response
      </td>
      <td> [Update response sample](rest-json/rest-json-interpreter-update-response.html)
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
      <td> [Delete response sample](rest-json/rest-json-interpreter-delete-response.html)
      </td>
    </tr>
  </table>
