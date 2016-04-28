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
 Zeppelin provides several REST APIs for interaction and remote activation of zeppelin functionality.

 All REST APIs are available starting with the following endpoint ```http://[zeppelin-server]:[zeppelin-port]/api```

 Note that zeppelin REST APIs receive or return JSON objects, it is recommended for you to install some JSON viewers
  such as [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc)


 If you work with zeppelin and find a need for an additional REST API please [file an issue or send us mail](../../community.html)

 <br />
### Notebook REST API list

  Notebooks REST API supports the following operations: List, Create, Get, Delete, Clone, Run, Export, Import as detailed in the following table

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List notebooks</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method lists the available notebooks on your server.
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
      <td>This ```POST``` method creates a new notebook using the given name or default name if none given.
          The body field of the returned JSON contains the new notebook id.
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
      <td><pre>{ "name": "name of new notebook" }</pre></td>
    </tr>
    <tr>
      <td> sample JSON input (with initial paragraphs) </td>
      <td><pre>
{
  "name": "name of new notebook",
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
}
      </pre></td>
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
      <th>Get notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method retrieves an existing notebook's information using the given id.
          The body field of the returned JSON contain information about paragraphs in the notebook.
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
        "jobName": "paragraph_1423500782552_-1439281894",
        "id": "20150210-015302_1492795503",
        "result": {
          "code": "SUCCESS",
          "type": "TABLE",
          "msg": "age\tvalue\n19\t4\n20\t3\n21\t7\n22\t9\n23\t20\n24\t24\n25\t44\n26\t77\n27\t94\n28\t103\n29\t97\n"
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
}
      </pre></td>
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
      <td>This ```DELETE``` method deletes a notebook by the given notebook id.
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
      <td>This ```POST``` method clones a notebook by the given id and create a new notebook using the given name
          or default name if none given.
          The body field of the returned JSON contains the new notebook id.
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
      <td>This ```POST``` method runs all paragraph in the given notebook id.
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
      <td>This ```DELETE``` method stops all paragraph in the given notebook id.
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
      <td>This ```GET``` method gets all paragraph status by the given notebook id.
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
      <td><pre>{"status":"OK","body":[{"id":"20151121-212654_766735423","status":"FINISHED","finished":"Tue Nov 24 14:21:40 KST 2015","started":"Tue Nov 24 14:21:39 KST 2015"},{"progress":"1","id":"20151121-212657_730976687","status":"RUNNING","finished":"Tue Nov 24 14:21:35 KST 2015","started":"Tue Nov 24 14:21:40 KST 2015"}]}</pre></td>
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
      <td>This ```POST``` method runs the paragraph by given notebook and paragraph id.
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
      <td> sample JSON input (optional, only needed when if you want to update dynamic form's value) </td>
      <td><pre>
{
  "name": "name of new notebook",
  "params": {
    "formLabel1": "value1",
    "formLabel2": "value2"
  }
}
      </pre></td>
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
      <td>This ```DELETE``` method stops the paragraph by given notebook and paragraph id.
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
      <td>This ```POST``` method adds cron job by the given notebook id.
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
      <td>This ```DELETE``` method removes cron job by the given notebook id.
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
      <th>Get cron job</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method gets cron job expression of given notebook id.
          The body field of the returned JSON contains the cron expression.
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

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Full-text search through the paragraphs in all notebooks</th>
      <th></th>
    </tr>
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
      <td><pre>{"status":"OK", body: [{"id":"<noteId>/paragraph/<paragraphId>", "name":"Notebook Name", "snippet":"", "text":""}]}</pre></td>
    </tr>
  </table>

<br/>


  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Create paragraph</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method create a new paragraph using JSON payload.
          The body field of the returned JSON contain the new paragraph id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]/paragraph```</td>
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
  }
      </pre></td>
    </tr>
    <tr>
      <td> sample JSON response </td>
      <td><pre>{"status": "CREATED","message": "","body": "20151218-100330_1754029574"}</pre></td>
    </tr>
  </table>

<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Get paragraph</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method retrieves an existing paragraph's information using the given id.
          The body field of the returned JSON contain information about paragraph.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]/paragraph/[paragraphId]```</td>
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
    "jobName": "paragraph_1450391574392_-1890856722",
    "id": "20151218-073254_1105602047",
    "result": {
      "code": "SUCCESS",
      "type": "TEXT",
      "msg": "it's paragraph2\n"
    },
    "dateCreated": "Dec 18, 2015 7:32:54 AM",
    "dateStarted": "Dec 18, 2015 7:33:55 AM",
    "dateFinished": "Dec 18, 2015 7:33:55 AM",
    "status": "FINISHED",
    "progressUpdateIntervalMs": 500
  }
}
      </pre></td>
    </tr>
  </table>

<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Move paragraph</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method moves a paragraph to the specific index (order) from the notebook.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]/paragraph/[paragraphId]/move/[newIndex]```</td>
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
      <th>Delete paragraph</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```DELETE``` method deletes a paragraph by the given notebook and paragraph id.
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/[notebookId]/paragraph/[paragraphId]```</td>
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



  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Export notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method exports a notebook by the given id and gernerates a JSON
      </td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/notebook/export/[notebookId]```</td>
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
      "jobName": "paragraph_1452300578795_1196072540",
      "id": "20160108-164938_1685162144",
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

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>Export notebook</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```POST``` method imports a notebook from the notebook JSON input
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
    <td> sample JSON input </td>
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
      "jobName": "paragraph_1452300578795_1196072540",
      "id": "20160108-164938_1685162144",
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
<tr>
      <td> sample JSON response </td>
      <td><pre>"status": "CREATED","message": "","body": "2AZPHY918"}</pre></td>
    </tr>
    </tr>
  </table>
