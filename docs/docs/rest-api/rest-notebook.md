---
layout: page
title: "Notebook REST API"
description: ""
group: rest-api
---
{% include JB/setup %}

## Zeppelin REST API
 Zeppelin provides several REST API's for interaction and remote activation of zeppelin functionality.
 
 All REST API are available starting with the following endpoint ```http://[zeppelin-server]:[zeppelin-port]/api```
 
 Note that zeppein REST API receive or return JSON objects, it it recommended you install some JSON view such as 
 [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc)
 
 
 If you work with zeppelin and find a need for an additional REST API please [file an issue or send us mail](../../community.html) 

 <br />
### Notebook REST API list
  
  Notebooks can be created, deleted or cloned using the following REST API
  
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
      <td> [Create JSON sample](rest-json/rest-json-notebook-create.json)</td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td> [Create response sample](rest-json/rest-json-notebook-create-response.json) </td>
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
      <td> [Delete response sample](rest-json/rest-json-notebook-delete-response.json) </td>
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
      <td> [Clone JSON sample](rest-json/rest-json-notebook-create.json)</td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td> [Clone response sample](rest-json/rest-json-notebook-create-response.json) </td>
    </tr>
  </table>
  
