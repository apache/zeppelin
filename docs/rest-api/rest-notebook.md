---
layout: page
title: "Apache Zeppelin Notebook REST API"
description: "This page contains Apache Zeppelin Notebook REST API information."
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

# Apache Zeppelin Notebook REST API

<div id="toc"></div>

## Overview
Apache Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.
All REST APIs are available starting with the following endpoint `http://[zeppelin-server]:[zeppelin-port]/api`. 
Note that Apache Zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc).

If you work with Apache Zeppelin and find a need for an additional REST API, please [file an issue or send us an email](http://zeppelin.apache.org/community.html).


## Notebook REST API List

  Notebooks REST API supports the following operations: List, Create, Get, Delete, Clone, Run, Export, Import as detailed in the following tables.

### List of the notes
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method lists the available notes on your server.
          Notebook JSON contains the ```name``` and ```id``` of all notes.
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
      <td><pre>
{
  "status": "OK",
  "message": "",
  "body": [
    {
      "name":"Homepage",
      "id":"2AV4WUEMK"
    },
    {
      "name":"Zeppelin Tutorial",
      "id":"2A94M5J1Z"
    }
  ]
}</pre></td>
    </tr>
  </table>

<br/>
### Create a new note
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method creates a new note using the given name or default name if none given.
          The body field of the returned JSON contains the new note id.
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
      <td> sample JSON input (without paragraphs) </td>
      <td><pre>{"name": "name of new note"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON input (with initial paragraphs) </td>
      <td><pre>
{
  "name": "name of new note",
  "paragraphs": [
    {
      "title": "paragraph title1",
      "text": "paragraph text1"
    },
    {
      "title": "paragraph title2",
      "text": "paragraph text2"
    }
  ]
}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>
{
  "status": "CREATED",
  "message": "",
  "body": "2AZPHY918"
}</pre></td>
    </tr>
  </table>

<br/>
### Get an existing note information
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method retrieves an existing note's information using the given id.
          The body field of the returned JSON contain information about paragraphs in the note.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]```</td>
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
      <td><pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "paragraphs": [
      {
        "text": "%sql \nselect age, count(1) value\nfrom bank \nwhere age < 30 \ngroup by age \norder by age",
        "config": {
          "colWidth": 4,
          "graph": {
            "mode": "multiBarChart",
            "height": 300,
            "optionOpen": false,
            "keys": [
              {
                "name": "age",
                "index": 0,
                "aggr": "sum"
              }
            ],
            "values": [
              {
                "name": "value",
                "index": 1,
                "aggr": "sum"
              }
            ],
            "groups": [],
            "scatter": {
              "xAxis": {
                "name": "age",
                "index": 0,
                "aggr": "sum"
              },
              "yAxis": {
                "name": "value",
                "index": 1,
                "aggr": "sum"
              }
            }
          }
        },
        "settings": {
          "params": {},
          "forms": {}
        },
        "jobName": "paragraph\_1423500782552\_-1439281894",
        "id": "20150210-015302\_1492795503",
        "results": {
          "code": "SUCCESS",
          "msg": [
            {
              "type": "TABLE",
              "data": "age\tvalue\n19\t4\n20\t3\n21\t7\n22\t9\n23\t20\n24\t24\n25\t44\n26\t77\n27\t94\n28\t103\n29\t97\n"
            }
          ]
        },
        "dateCreated": "Feb 10, 2015 1:53:02 AM",
        "dateStarted": "Jul 3, 2015 1:43:17 PM",
        "dateFinished": "Jul 3, 2015 1:43:23 PM",
        "status": "FINISHED",
        "progressUpdateIntervalMs": 500
      }
    ],
    "name": "Zeppelin Tutorial",
    "id": "2A94M5J1Z",
    "angularObjects": {},
    "config": {
      "looknfeel": "default"
    },
    "info": {}
  }
}</pre></td>
    </tr>
  </table>

<br/>
### Delete a note
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes a note by the given note id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]```</td>
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
      <td><pre>{"status": "OK","message": ""}</pre></td>
    </tr>
  </table>

<br/>
### Clone a note
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method clones a note by the given id and create a new note using the given name
          or default name if none given.
          The body field of the returned JSON contains the new note id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]```</td>
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
      <td><pre>{"name": "name of new note"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>
{
  "status": "CREATED",
  "message": "",
  "body": "2AZPHY918"
}</pre></td>
    </tr>
  </table>

<br/>
### Run all paragraphs
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>
      This ```POST``` method runs all paragraphs in the given note id. <br />
      If you can not find Note id 404 returns.
      If there is a problem with the interpreter returns a 412 error.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 404 or 412</td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
    <tr>
       <td> sample JSON error response </td>
       <td>
         <pre>
           {
             "status": "NOT_FOUND",
             "message": "note not found."
           }
         </pre><br />
         <pre>
           {
             "status": "PRECONDITION_FAILED",
             "message": "paragraph_1469771130099_-278315611 Not selected or Invalid Interpreter bind"
           }
         </pre>
       </td>
    </tr>
  </table>

<br/>
### Stop all paragraphs
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method stops all paragraphs in the given note id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]```</td>
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
### Get the status of all paragraphs
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method gets the status of all paragraphs by the given note id.
          The body field of the returned JSON contains of the array that compose of the paragraph id, paragraph status, paragraph finish date, paragraph started date.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]```</td>
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
      <td><pre>
{
  "status": "OK",
  "body": [
    {
      "id":"20151121-212654\_766735423",
      "status":"FINISHED",
      "finished":"Tue Nov 24 14:21:40 KST 2015",
      "started":"Tue Nov 24 14:21:39 KST 2015"
    },
    {
      "progress":"1",
      "id":"20151121-212657\_730976687",
      "status":"RUNNING",
      "finished":"Tue Nov 24 14:21:35 KST 2015",
      "started":"Tue Nov 24 14:21:40 KST 2015"
    }
  ]
}</pre></td>
    </tr>
  </table>

<br/>
### Get the status of a single paragraph
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method gets the status of a single paragraph by the given note and paragraph id.
          The body field of the returned JSON contains of the array that compose of the paragraph id, paragraph status, paragraph finish date, paragraph started date.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]/[paragraphId]```</td>
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
      <td><pre>
{
  "status": "OK",
  "body": {
      "id":"20151121-212654\_766735423",
      "status":"FINISHED",
      "finished":"Tue Nov 24 14:21:40 KST 2015",
      "started":"Tue Nov 24 14:21:39 KST 2015"
    }
}</pre></td>
    </tr>
  </table>

<br/>
### Run a paragraph asynchronously
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method runs the paragraph asynchronously by given note and paragraph id. This API always return SUCCESS even if the execution of the paragraph fails later because the API is asynchronous
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]/[paragraphId]```</td>
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
      <td> sample JSON input (optional, only needed when if you want to update dynamic form's value) </td>
      <td><pre>
{
  "name": "name of new note",
  "params": {
    "formLabel1": "value1",
    "formLabel2": "value2"
  }
}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
  </table>

<br/>
### Run a paragraph synchronously
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method runs the paragraph synchronously by given note and paragraph id. This API can return SUCCESS or ERROR depending on the outcome of the paragraph execution
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/run/[noteId]/[paragraphId]```</td>
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
      <td> sample JSON input (optional, only needed when if you want to update dynamic form's value) </td>
      <td><pre>
{
  "name": "name of new note",
  "params": {
    "formLabel1": "value1",
    "formLabel2": "value2"
  }
}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "OK"}</pre></td>
    </tr>    
    <tr>
      <td> sample JSON error </td>
      <td><pre>
{
   "status": "INTERNAL\_SERVER\_ERROR",
   "body": {
       "code": "ERROR",
       "type": "TEXT",
       "msg": "bash: -c: line 0: unexpected EOF while looking for matching ``'\nbash: -c: line 1: syntax error: unexpected end of file\nExitValue: 2"
   }
}</pre></td>
    </tr>
  </table>

<br/>
### Stop a paragraph
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method stops the paragraph by given note and paragraph id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/job/[noteId]/[paragraphId]```</td>
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
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
  </table>

<br/>
### Add Cron Job
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method adds cron job by the given note id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[noteId]```</td>
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
      <td><pre>{"cron": "cron expression of note"}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
  </table>

<br/>

### Remove Cron Job
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method removes cron job by the given note id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[noteId]```</td>
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
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
  </table>

<br/>

### Get Cron Job
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method gets cron job expression of given note id.
          The body field of the returned JSON contains the cron expression.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/cron/[noteId]```</td>
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
      <td><pre>{"status": "OK", "body": "* * * * * ?"}</pre></td>
    </tr>
  </table>

<br />
### Full text search through the paragraphs in all notes
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>```GET``` request will return list of matching paragraphs
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/search?q=[query]```</td>
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
      <td>Sample JSON response </td>
      <td><pre>
{
  "status": "OK",
  "body": [
    {
      "id": "<noteId>/paragraph/<paragraphId>",
      "name":"Note Name", 
      "snippet":"",
      "text":""
    }
  ]
}</pre></td>
    </tr>
  </table>

<br/>
### Create a new paragraph
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method create a new paragraph using JSON payload.
          The body field of the returned JSON contain the new paragraph id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/paragraph```</td>
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
      <td> sample JSON input (add to the last) </td>
      <td><pre>
{
  "title": "Paragraph insert revised",
  "text": "%spark\nprintln(\"Paragraph insert revised\")"
}</pre></td>
    </tr>
    <tr>
      <td> sample JSON input (add to specific index) </td>
      <td><pre>
{
  "title": "Paragraph insert revised",
  "text": "%spark\nprintln(\"Paragraph insert revised\")",
  "index": 0
}</pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>
{
  "status": "CREATED",
  "message": "",
  "body": "20151218-100330\_1754029574"
}</pre></td>
    </tr>
  </table>

<br/>
### Get a paragraph information
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method retrieves an existing paragraph's information using the given id.
          The body field of the returned JSON contain information about paragraph.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/paragraph/[paragraphId]```</td>
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
      <td><pre>
{
  "status": "OK",
  "message": "",
  "body": {
    "title": "Paragraph2",
    "text": "%spark\n\nprintln(\"it's paragraph2\")",
    "dateUpdated": "Dec 18, 2015 7:33:54 AM",
    "config": {
      "colWidth": 12,
      "graph": {
        "mode": "table",
        "height": 300,
        "optionOpen": false,
        "keys": [],
        "values": [],
        "groups": [],
        "scatter": {}
      },
      "enabled": true,
      "title": true,
      "editorMode": "ace/mode/scala"
    },
    "settings": {
      "params": {},
      "forms": {}
    },
    "jobName": "paragraph\_1450391574392\_-1890856722",
    "id": "20151218-073254\_1105602047",
    "results": {
      "code": "SUCCESS",
      "msg": [
        {
           "type": "TEXT",
           "data": "it's paragraph2\n"
        }
      ]
    },
    "dateCreated": "Dec 18, 2015 7:32:54 AM",
    "dateStarted": "Dec 18, 2015 7:33:55 AM",
    "dateFinished": "Dec 18, 2015 7:33:55 AM",
    "status": "FINISHED",
    "progressUpdateIntervalMs": 500
  }
}</pre></td>
    </tr>
  </table>

<br/>
### Update paragraph configuration
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method update paragraph configuration using given id so that user can change paragraph setting such as graph type, show or hide editor/result and paragraph size, etc. You can update certain fields you want, for example you can update <code>colWidth</code> field only by sending request with payload <code>{"colWidth": 12.0}</code>.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/paragraph/[paragraphId]/config```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Bad Request code</td>
      <td>400</td>
    </tr>
    <tr>
      <td>Forbidden code</td>
      <td>403</td>
    </tr>
    <tr>
      <td>Not Found code</td>
      <td>404</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>500</td>
    </tr>
    <tr>
      <td>sample JSON input</td>
      <td><pre>
{
  "colWidth": 6.0,
  "graph": {
    "mode": "lineChart",
    "height": 200.0,
    "optionOpen": false,
    "keys": [
      {
        "name": "age",
        "index": 0.0,
        "aggr": "sum"
      }
    ],
    "values": [
      {
        "name": "value",
        "index": 1.0,
        "aggr": "sum"
      }
    ],
    "groups": [],
    "scatter": {}
  },
  "editorHide": true,
  "editorMode": "ace/mode/markdown",
  "tableHide": false
}</pre></td>
    </tr>
    <tr>
      <td>sample JSON response</td>
      <td><pre>
{
  "status":"OK",
  "message":"",
  "body":{
    "text":"%sql \nselect age, count(1) value\nfrom bank \nwhere age \u003c 30 \ngroup by age \norder by age",
    "config":{
      "colWidth":6.0,
      "graph":{
        "mode":"lineChart",
        "height":200.0,
        "optionOpen":false,
        "keys":[
          {
            "name":"age",
            "index":0.0,
            "aggr":"sum"
          }
        ],
        "values":[
          {
            "name":"value",
            "index":1.0,
            "aggr":"sum"
          }
        ],
        "groups":[],
        "scatter":{}
      },
      "tableHide":false,
      "editorMode":"ace/mode/markdown",
      "editorHide":true
    },
    "settings":{
      "params":{},
      "forms":{}
    },
    "apps":[],
    "jobName":"paragraph_1423500782552_-1439281894",
    "id":"20150210-015302_1492795503",
    "results":{
      "code":"SUCCESS",
      "msg": [
        {
          "type":"TABLE",
          "data":"age\tvalue\n19\t4\n20\t3\n21\t7\n22\t9\n23\t20\n24\t24\n25\t44\n26\t77\n27\t94\n28\t103\n29\t97\n"
        }
      ]
    },
    "dateCreated":"Feb 10, 2015 1:53:02 AM",
    "dateStarted":"Jul 3, 2015 1:43:17 PM",
    "dateFinished":"Jul 3, 2015 1:43:23 PM",
    "status":"FINISHED",
    "progressUpdateIntervalMs":500
  }
}</pre></td>
    </tr>
  </table>

<br/>
### Move a paragraph to the specific index
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method moves a paragraph to the specific index (order) from the note.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/paragraph/[paragraphId]/move/[newIndex]```</td>
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
      <td><pre>{"status": "OK","message": ""}</pre></td>
    </tr>
  </table>

<br/>
### Delete a paragraph
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes a paragraph by the given note and paragraph id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/paragraph/[paragraphId]```</td>
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
      <td><pre>{"status": "OK","message": ""}</pre></td>
    </tr>
  </table>

<br />
### Export a note
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```GET``` method exports a note by the given id and gernerates a JSON
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/export/[noteId]```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>201</td>
    </tr>
    <tr>
      <td> Fail code</td>
      <td> 500 </td>
    </tr>
    <td> sample JSON response </td>
      <td><pre>{
  "paragraphs": [
    {
      "text": "%md This is my new paragraph in my new note",
      "dateUpdated": "Jan 8, 2016 4:49:38 PM",
      "config": {
        "enabled": true
      },
      "settings": {
        "params": {},
        "forms": {}
      },
      "jobName": "paragraph\_1452300578795\_1196072540",
      "id": "20160108-164938\_1685162144",
      "dateCreated": "Jan 8, 2016 4:49:38 PM",
      "status": "READY",
      "progressUpdateIntervalMs": 500
    }
  ],
  "name": "source note for export",
  "id": "2B82H3RR1",
  "angularObjects": {},
  "config": {},
  "info": {}
}</pre></td>
    </tr>
  </table>

<br />
### Import a note
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```POST``` method imports a note from the note JSON input
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/import```</td>
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
      <td>sample JSON input</td>
      <td><pre>
{
  "paragraphs": [
    {
      "text": "%md This is my new paragraph in my new note",
      "dateUpdated": "Jan 8, 2016 4:49:38 PM",
      "config": {
        "enabled": true
      },
      "settings": {
        "params": {},
        "forms": {}
      },
      "jobName": "paragraph\_1452300578795\_1196072540",
      "id": "20160108-164938\_1685162144",
      "dateCreated": "Jan 8, 2016 4:49:38 PM",
      "status": "READY",
      "progressUpdateIntervalMs": 500
    }
  ],
  "name": "source note for export",
  "id": "2B82H3RR1",
  "angularObjects": {},
  "config": {},
  "info": {}
}</pre></td>
    </tr>
    <tr>
      <td>sample JSON response</td>
      <td><pre>
{
  "status": "CREATED",
  "message": "",
  "body": "2AZPHY918"
}</pre></td>
    </tr>
  </table>

<br />
### Clear all paragraph result
  <table class="table-configuration">
    <col width="200">
    <tr>
      <td>Description</td>
      <td>This ```PUT``` method clear all paragraph results from note of given id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[noteId]/clear```</td>
    </tr>
    <tr>
      <td>Success code</td>
      <td>200</td>
    </tr>
    <tr>
      <td>Forbidden code</td>
      <td>401</td>
    </tr>
    <tr>
      <td>Not Found code</td>
      <td>404</td>
    </tr>
    <tr>
      <td>Fail code</td>
      <td>500</td>
    </tr>
    <tr>
      <td>sample JSON response</td>
      <td><pre>{"status": "OK"}</pre></td>
    </tr>
    </tr>
  </table>
