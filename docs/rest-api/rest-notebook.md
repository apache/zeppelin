---
layout: page
title: "Notebook REST API"
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
### Notebook REST API list
  
  Notebooks REST API supports the following operations: List, Create, Delete & Clone as detailed in the following table 
  
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List notebooks</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method list the available notebooks on your server.
          Notebook JSON contains the ```name``` and ```id``` of all notebooks.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK","message":"","body":[{"name":"Homepage","id":"2AV4WUEMK"},{"name":"Zeppelin Tutorial","id":"2A94M5J1Z"}]}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Create notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method create a new notebook using the given name or default name if none given.
          The body field of the returned JSON contain the new notebook id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook```</td>
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
      <td> sample JSON input </td>
      <td><pre>{"name": "name of new notebook"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "CREATED","message": "","body": "2AZPHY918"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Delete notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method delete a notebook by the given notebook id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK","message":""}</pre></td>
    </tr>
  </table>
  
<br/>
  
  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Clone notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method clone a notebook by the given id and create a new notebook using the given name 
          or default name if none given.
          The body field of the returned JSON contain the new notebook id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]```</td>
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
      <td> sample JSON input </td>
      <td><pre>{"name": "name of new notebook"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "CREATED","message": "","body": "2AZPHY918"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Run notebook job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method run all paragraph in the given notebook id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Stop notebook job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method stop all paragraph in the given notebook id. 
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Get notebook job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method get all paragraph status by the given notebook id. 
          The body field of the returned JSON contains of the array that compose of the paragraph id, paragraph status, paragraph finish date, paragraph started date.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK","body":[{"id":"20151121-212654_766735423","status":"FINISHED","finished":"Tue Nov 24 14:21:40 KST 2015","started":"Tue Nov 24 14:21:39 KST 2015"},{"id":"20151121-212657_730976687","status":"FINISHED","finished":"Tue Nov 24 14:21:40 KST 2015","started":"Tue Nov 24 14:21:40 KST 2015"}]}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Run paragraph job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method run the paragraph by given notebook and paragraph id. 
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[notebookId]/[paragraphId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Stop paragraph job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method stop the paragraph by given notebook and paragraph id. 
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[notebookId]/[paragraphId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Add cron job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method add cron job by the given notebook id. 
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[notebookId]```</td>
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
      <td> sample JSON input </td>
      <td><pre>{"cron": "cron expression of notebook"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Remove cron job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method remove cron job by the given notebook id. 
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK"}</pre></td>
    </tr>
  </table>
  
<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Get clone job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method get cron job expression of given notebook id. 
          The body field of the returned JSON contain the cron expression.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[notebookId]```</td>
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
      <td> sample JSON response </td>
      <td><pre>{"status":"OK","body":"* * * * * ?"}</pre></td>
    </tr>
  </table>
  
