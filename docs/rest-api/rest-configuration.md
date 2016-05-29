---
layout: page
title: "Configuration REST API"
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

 Note that Zeppelin REST API receive or return JSON objects, it it recommended you install some JSON viewers such as 
 [JSONView](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc)


 If you work with zeppelin and find a need for an additional REST API please [file an issue or send us mail](../../community.html)

 <br />
### Configuration REST API list

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List configurations</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all key/value pair of configurations on the server.<br/>
      Note: For security reason, some pairs would not be shown.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/configurations/all```</td>
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
   "status":"OK",
   "message":"",
   "body":{  
      "zeppelin.war.tempdir":"webapps",
      "zeppelin.notebook.homescreen.hide":"false",
      "zeppelin.interpreter.remoterunner":"bin/interpreter.sh",
      "zeppelin.notebook.s3.user":"user",
      "zeppelin.server.port":"8089",
      "zeppelin.dep.localrepo":"local-repo",
      "zeppelin.ssl.truststore.type":"JKS",
      "zeppelin.ssl.keystore.path":"keystore",
      "zeppelin.notebook.s3.bucket":"zeppelin",
      "zeppelin.server.addr":"0.0.0.0",
      "zeppelin.ssl.client.auth":"false",
      "zeppelin.server.context.path":"/",
      "zeppelin.ssl.keystore.type":"JKS",
      "zeppelin.ssl.truststore.path":"truststore",
      "zeppelin.interpreters":"org.apache.zeppelin.spark.SparkInterpreter,org.apache.zeppelin.spark.PySparkInterpreter,org.apache.zeppelin.spark.SparkRInterpreter,org.apache.zeppelin.spark.SparkSqlInterpreter,org.apache.zeppelin.spark.DepInterpreter,org.apache.zeppelin.markdown.Markdown,org.apache.zeppelin.angular.AngularInterpreter,org.apache.zeppelin.shell.ShellInterpreter,org.apache.zeppelin.hive.HiveInterpreter,org.apache.zeppelin.tajo.TajoInterpreter,org.apache.zeppelin.flink.FlinkInterpreter,org.apache.zeppelin.lens.LensInterpreter,org.apache.zeppelin.ignite.IgniteInterpreter,org.apache.zeppelin.ignite.IgniteSqlInterpreter,org.apache.zeppelin.cassandra.CassandraInterpreter,org.apache.zeppelin.geode.GeodeOqlInterpreter,org.apache.zeppelin.postgresql.PostgreSqlInterpreter,org.apache.zeppelin.phoenix.PhoenixInterpreter,org.apache.zeppelin.kylin.KylinInterpreter,org.apache.zeppelin.elasticsearch.ElasticsearchInterpreter,org.apache.zeppelin.scalding.ScaldingInterpreter",
      "zeppelin.ssl":"false",
      "zeppelin.notebook.autoInterpreterBinding":"true",
      "zeppelin.notebook.homescreen":"",
      "zeppelin.notebook.storage":"org.apache.zeppelin.notebook.repo.VFSNotebookRepo",
      "zeppelin.interpreter.connect.timeout":"30000",
      "zeppelin.anonymous.allowed":"true",
      "zeppelin.server.allowed.origins":"*",
      "zeppelin.encoding":"UTF-8"
   }
}
        </pre>
      </td>
    </tr>
  </table>

<br/>

  <table class="table-configuration">
    <col width="200">
    <tr>
      <th>List configurations (prefix match)</th>
      <th></th>
    </tr>
    <tr>
      <td>Description</td>
      <td>This ```GET``` method return all prefix matched key/value pair of configurations on the server.<br/>
      Note: For security reason, some pairs would not be shown.</td>
    </tr>
    <tr>
      <td>URL</td>
      <td>```http://[zeppelin-server]:[zeppelin-port]/api/configurations/prefix/[prefix]```</td>
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
   "status":"OK",
   "message":"",
   "body":{  
      "zeppelin.ssl.keystore.type":"JKS",
      "zeppelin.ssl.truststore.path":"truststore",
      "zeppelin.ssl.truststore.type":"JKS",
      "zeppelin.ssl.keystore.path":"keystore",
      "zeppelin.ssl":"false",
      "zeppelin.ssl.client.auth":"false"
   }
}
        </pre>
      </td>
    </tr>
  </table>
