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
  
  Notebooks REST API supports the following operations: List, Create, Get, Delete, Clone, Run as detailed in the following table 
  
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
        "title": "Load data into table",
        "text": "import org.apache.commons.io.IOUtils\nimport java.net.URL\nimport java.nio.charset.Charset\n\n// Zeppelin creates and injects sc (SparkContext) and sqlContext (HiveContext or SqlContext)\n// So you don't need create them manually\n\n// load bank data\nval bankText = sc.parallelize(\n    IOUtils.toString(\n        new URL(\"https://s3.amazonaws.com/apache-zeppelin/tutorial/bank/bank.csv\"),\n        Charset.forName(\"utf8\")).split(\"\n\"))\n\ncase class Bank(age: Integer, job: String, marital: String, education: String, balance: Integer)\n\nval bank = bankText.map(s => s.split(\";\")).filter(s => s(0) != \"\"age\"\").map(\n    s => Bank(s(0).toInt, \n            s(1).replaceAll(\"\"\", \"\"),\n            s(2).replaceAll(\"\"\", \"\"),\n            s(3).replaceAll(\"\"\", \"\"),\n            s(5).replaceAll(\"\"\", \"\").toInt\n        )\n).toDF()\nbank.registerTempTable(\"bank\")",
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
          "title": true
        },
        "settings": {
          "params": {},
          "forms": {}
        },
        "jobName": "paragraph_1423500779206_-1502780787",
        "id": "20150210-015259_1403135953",
        "result": {
          "code": "SUCCESS",
          "type": "TEXT",
          "msg": "import org.apache.commons.io.IOUtils\nimport java.net.URL\nimport java.nio.charset.Charset\nbankText: org.apache.spark.rdd.RDD[String] = ParallelCollectionRDD[32] at parallelize at <console>:65\ndefined class Bank\nbank: org.apache.spark.sql.DataFrame = [age: int, job: string, marital: string, education: string, balance: int]\n"
        },
        "dateCreated": "Feb 10, 2015 1:52:59 AM",
        "dateStarted": "Jul 3, 2015 1:43:40 PM",
        "dateFinished": "Jul 3, 2015 1:43:45 PM",
        "status": "FINISHED",
        "progressUpdateIntervalMs": 500
      },
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
      },
      {
        "text": "%sql \nselect age, count(1) value \nfrom bank \nwhere age < ${maxAge=30} \ngroup by age \norder by age",
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
          "params": {
            "maxAge": "35"
          },
          "forms": {
            "maxAge": {
              "name": "maxAge",
              "defaultValue": "30",
              "hidden": false
            }
          }
        },
        "jobName": "paragraph_1423720444030_-1424110477",
        "id": "20150212-145404_867439529",
        "result": {
          "code": "SUCCESS",
          "type": "TABLE",
          "msg": "age\tvalue\n19\t4\n20\t3\n21\t7\n22\t9\n23\t20\n24\t24\n25\t44\n26\t77\n27\t94\n28\t103\n29\t97\n30\t150\n31\t199\n32\t224\n33\t186\n34\t231\n"
        },
        "dateCreated": "Feb 12, 2015 2:54:04 PM",
        "dateStarted": "Jul 3, 2015 1:43:28 PM",
        "dateFinished": "Jul 3, 2015 1:43:29 PM",
        "status": "FINISHED",
        "progressUpdateIntervalMs": 500
      },
      {
        "text": "%sql \nselect age, count(1) value \nfrom bank \nwhere marital=\"${marital=single,single|divorced|married}\" \ngroup by age \norder by age",
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
          "params": {
            "marital": "single"
          },
          "forms": {
            "marital": {
              "name": "marital",
              "defaultValue": "single",
              "options": [
                {
                  "value": "single"
                },
                {
                  "value": "divorced"
                },
                {
                  "value": "married"
                }
              ],
              "hidden": false
            }
          }
        },
        "jobName": "paragraph_1423836262027_-210588283",
        "id": "20150213-230422_1600658137",
        "result": {
          "code": "SUCCESS",
          "type": "TABLE",
          "msg": "age\tvalue\n19\t4\n20\t3\n21\t7\n22\t9\n23\t17\n24\t13\n25\t33\n26\t56\n27\t64\n28\t78\n29\t56\n30\t92\n31\t86\n32\t105\n33\t61\n34\t75\n35\t46\n36\t50\n37\t43\n38\t44\n39\t30\n40\t25\n41\t19\n42\t23\n43\t21\n44\t20\n45\t15\n46\t14\n47\t12\n48\t12\n49\t11\n50\t8\n51\t6\n52\t9\n53\t4\n55\t3\n56\t3\n57\t2\n58\t7\n59\t2\n60\t5\n66\t2\n69\t1\n"
        },
        "dateCreated": "Feb 13, 2015 11:04:22 PM",
        "dateStarted": "Jul 3, 2015 1:43:33 PM",
        "dateFinished": "Jul 3, 2015 1:43:34 PM",
        "status": "FINISHED",
        "progressUpdateIntervalMs": 500
      },
      {
        "config": {},
        "settings": {
          "params": {},
          "forms": {}
        },
        "jobName": "paragraph_1435955447812_-158639899",
        "id": "20150703-133047_853701097",
        "dateCreated": "Jul 3, 2015 1:30:47 PM",
        "status": "READY",
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
  
