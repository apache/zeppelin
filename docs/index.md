---
layout: page
title: Overview
tagline: Less Development, More analysis!
group: nav-right
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
<br />
<div class="row">
 <div class="col-md-5">
<h2>Multi-purpose Notebook</h2>

<p style="font-size:16px; color:#555555;font-style:italic;margin-bottom: 15px;">
  The Notebook is the place for all your needs
</p>
<ul style="list-style-type: none;padding-left:10px;" >
  <li style="font-size:20px; margin: 5px;"><span class="glyphicon glyphicon-import"></span> Data Ingestion</li>
  <li style="font-size:20px; margin: 5px;"><span class="glyphicon glyphicon-eye-open"></span> Data Discovery</li>
  <li style="font-size:20px; margin: 5px;"><span class="glyphicon glyphicon-wrench"></span> Data Analytics</li>
  <li style="font-size:20px; margin: 5px;"><span class="glyphicon glyphicon-dashboard"></span> Data Visualization & Collaboration</li>
</ul>

 </div>
 <div class="col-md-7"><img class="img-responsive" style="border: 1px solid #ecf0f1;" height="auto" src="/assets/themes/zeppelin/img/notebook.png" /></div>
</div>


<br />
### Multiple language backend

Zeppelin interpreter concept allows any language/data-processing-backend to be plugged into Zeppelin.
Currently Zeppelin supports many interpreters such as Scala(with Apache Spark), Python(with Apache Spark), SparkSQL, Hive, Markdown and Shell.

<img class="img-responsive" src="/assets/themes/zeppelin/img/screenshots/multiple_language_backend.png" />

Adding new language-backend is really simple. Learn [how to write a zeppelin interpreter](./development/writingzeppelininterpreter.html).

<br />
### Apache Spark integration

Zeppelin provides built-in Apache Spark integration. You don't need to build a separate module, plugin or library for it.

<img src="/assets/themes/zeppelin/img/spark_logo.jpg" width="80px" />

Zeppelin's Spark integration provides

- Automatic SparkContext and SQLContext injection
- Runtime jar dependency loading from local filesystem or maven repository. Learn more about [dependency loader](./interpreter/spark.html#dependencyloading).
- Canceling job and displaying its progress

<br />
### Data visualization

Some basic charts are already included in Zeppelin. Visualizations are not limited to SparkSQL's query, any output from any language backend can be recognized and visualized.

<div class="row">
  <div class="col-md-6">
    <img class="img-responsive" src="/assets/themes/zeppelin/img/graph1.png" />
  </div>
  <div class="col-md-6">
    <img class="img-responsive" src="/assets/themes/zeppelin/img/graph2.png" />
  </div>
</div>

#### Pivot chart

With simple drag and drop Zeppelin aggeregates the values and display them in pivot chart. You can easily create chart with multiple aggregated values including sum, count, average, min, max.

<div class="row">
  <div class="col-md-8">
    <img class="img-responsive" src="/assets/themes/zeppelin/img/screenshots/pivot.png" />
  </div>
</div>
Learn more about Zeppelin's Display system. ( [text](./displaysystem/display.html), [html](./displaysystem/display.html#html), [table](./displaysystem/table.html), [angular](./displaysystem/angular.html) )


<br />
### Dynamic forms

Zeppelin can dynamically create some input forms into your notebook.

<img class="img-responsive" src="/assets/themes/zeppelin/img/screenshots/form_input.png" />

Learn more about [Dynamic Forms](./manual/dynamicform.html).


<br />
### Collaboration

Notebook URL can be shared among collaborators. Zeppelin can then broadcast any changes in realtime, just like the collaboration in Google docs.

<img src="/assets/themes/zeppelin/img/screenshots/collaboration.png" />

<br />
### Publish

<p>Zeppelin provides an URL to display the result only, that page does not include Zeppelin's menu and buttons.
This way, you can easily embed it as an iframe inside of your website.</p>

<div class="row">
  <img class="img-responsive center-block" src="/assets/themes/zeppelin/img/screenshots/publish.png" />
</div>

<br />
### 100% Opensource

Apache Zeppelin (incubating) is Apache2 Licensed software. Please check out the [source repository](https://github.com/apache/incubator-zeppelin) and [How to contribute](./development/howtocontribute.html)

Zeppelin has a very active development community.
Join the [Mailing list](./community.html) and report issues on our [Issue tracker](https://issues.apache.org/jira/browse/ZEPPELIN).

<br />
### Undergoing Incubation
Apache Zeppelin is an effort undergoing [incubation](https://incubator.apache.org/index.html) at The Apache Software Foundation (ASF), sponsored by the Incubator. Incubation is required of all newly accepted projects until a further review indicates that the infrastructure, communications, and decision making process have stabilized in a manner consistent with other successful ASF projects. While incubation status is not necessarily a reflection of the completeness or stability of the code, it does indicate that the project has yet to be fully endorsed by the ASF.
 
