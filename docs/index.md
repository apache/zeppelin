---
layout: page
title:
description:
group:
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
  <div class="col-md-6" style="padding-right:0;">
    <h1 style="color:#4c555a">What is Apache Zeppelin?</h1>
    <p style="margin-bottom: 0px; margin-top: 20px; font-size: 18px; font-style="font-family: "Roboto", sans-serif;">
      Multi-purpose notebook which supports
    </p>
    <p style="font-size: 18px; font-style="font-family: "Roboto", sans-serif;">
      20+ language backends
    </p>
    <ul style="list-style-type: none; padding-left:10px; margin-top: 30px;" >
      <li style="font-weight: 300; font-size:18px; margin: 5px;"><span class="glyphicon glyphicon-import" style="margin-right:10px"></span> Data Ingestion</li>
      <li style="font-weight: 300; font-size:18px; margin: 5px;"><span class="glyphicon glyphicon-eye-open" style="margin-right:10px"></span> Data Discovery</li>
      <li style="font-weight: 300; font-size:18px; margin: 5px;"><span class="glyphicon glyphicon-wrench" style="margin-right:10px"></span> Data Analytics</li>
      <li style="font-weight: 300; font-size:18px; margin: 5px;"><span class="glyphicon glyphicon-dashboard" style="margin-right:10px"></span> Data Visualization & Collaboration</li>
    </ul>
    <br/>
  </div>
  <div class="col-md-6" style="padding:0; margin-top: 50px;">
    <img class="img-responsive" style="border: 1px solid #ecf0f1;" src="./assets/themes/zeppelin/img/notebook.png" />
  </div>
</div>

## Documentation 

#### Quick Start

* [Installation](./install/install.html) for basic instructions on installing Apache Zeppelin
* [Tutorial: Spark]()
* [Tutorial: JDBC]()
* [Tutorial: Python]()
* [Explore Zeppelin UI](./quickstart/explorezeppelinui.html): basic components of Apache Zeppelin home
  
#### User Guide
* Dynamic Form 
  * [Usage](./manual/dynamicform.html): a step by step guide for creating dynamic forms
* Display System 
  * [Text Display (`%text`)](./displaysystem/basicdisplaysystem.html#text)
  * [HTML Display (`%html`)](./displaysystem/basicdisplaysystem.html#html)
  * [Table Display (`%table`)](./displaysystem/basicdisplaysystem.html#table)
  * [Angular Display using Backend API (`%angular`)](./displaysystem/back-end-angular.html)
  * [Angular Display using Frontend API (`%angular`)](./displaysystem/front-end-angular.html)
* Interpreter  
  * [Overview](./manual/interpreters.html): what is interpreter group? how can you set interpreters in Apache Zeppelin?
  * [User Impersonation](./manual/userimpersonation.html) when you want to run interpreter as end user
  * [Dependency Management](./manual/dependencymanagement.html) when you include external libraries to interpreter
  * [Installation](./manual/interpreterinstallation.html): Install not only community managed interpreters but also 3rd party interpreters
  * [Execution Hooks](./manual/interpreterexechooks.html) to specify additional code to be executed by an interpreter at pre and post-paragraph code execution
* REST API: available REST API list in Apache Zeppelin
  * [Zeppelin server API](./rest-api/rest-zeppelin-server.html)
  * [Interpreter API](./rest-api/rest-interpreter.html)
  * [Notebook API](./rest-api/rest-notebook.html)
  * [Notebook Repository API](./rest-api/rest-notebookRepo.html)
  * [Configuration API](./rest-api/rest-configuration.html)
  * [Credential API](./rest-api/rest-credential.html)
  * [Helium API](./rest-api/rest-helium.html)
* Advanced:
  * [Publishing Paragraphs](./manual/publish.html) results into your external website
  * [Customizing Zeppelin Homepage](./manual/notebookashomepage.html) with one of your notebooks
  
#### Admin Guide
* [How to Build Zeppelin](./install/build.html)
* Setup Zeppelin on Cluster 
  * [Zeppelin on Spark Cluster Mode (Standalone via Docker)](./install/spark_cluster_mode.html#spark-standalone-mode)
  * [Zeppelin on Spark Cluster Mode (YARN via Docker)](./install/spark_cluster_mode.html#spark-on-yarn-mode)
  * [Zeppelin on Spark Cluster Mode (Mesos via Docker)](./install/spark_cluster_mode.html#spark-on-mesos-mode)
  * [Zeppelin on CDH (via Docker)](./install/cdh.html)
  * [Apache Zeppelin on Vagrant VM](./install/virtual_machine.html)
* Security: available security support in Apache Zeppelin
  * [Authentication for NGINX](./security/authentication.html)
  * [Shiro Authentication](./security/shiroauthentication.html)
  * [Notebook Authorization](./security/notebook_authorization.html)
  * [Data Source Authorization](./security/datasource_authorization.html)
  * [Helium Authorization](./security/helium_authorization.html)
* Notebook Storage: a guide about saving notebooks to external storage
  * [Git Storage](./storage/storage.html#notebook-storage-in-local-git-repository)
  * [S3 Storage](./storage/storage.html#notebook-storage-in-s3)
  * [Azure Storage](./storage/storage.html#notebook-storage-in-azure)
  * [ZeppelinHub Storage](./storage/storage.html#storage-in-zeppelinhub)
* [Configuration](./install/configuration.html): lists for Apache Zeppelin
* [Upgrading](./install/upgrade.html): a manual procedure of upgrading Apache Zeppelin version
* [Trouble Shooting]()
  
#### Developer Guide
* Extending Zeppelin
  * [Writing Zeppelin Interpreter](./development/writingzeppelininterpreter.html)
  * [Helium: Overview](./development/writingzeppelinapplication.html)
  * [Helium: Writing Application](./development/writingzeppelinapplication.html)
  * [Helium: Writing Spell](./development/writingzeppelinspell.html)
  * [Helium: Writing Visualization: Basic](./development/writingzeppelinvisualization.html)
  * [Helium: Writing Visualization: Transformation](./development/writingzeppelinvisualization_transformation.html)
* Contributing to Zeppelin
  * [How to Contribute (code)](./development/howtocontribute.html)
  * [How to Contribute (website)](./development/howtocontributewebsite.html)

#### External Resources
  * [Mailing List](https://zeppelin.apache.org/community.html)
  * [Apache Zeppelin Wiki](https://cwiki.apache.org/confluence/display/ZEPPELIN/Zeppelin+Home)
  * [StackOverflow tag `apache-zeppelin`](http://stackoverflow.com/questions/tagged/apache-zeppelin)
  * [Articles]()
