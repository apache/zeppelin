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
<div class="row" style="margin-top: 0px;">
  <div class="col-sm-6 col-md-6" style="padding-right:0;">
    <h1 style="color:#4c555a; margin-top: 0px;">What is Apache Zeppelin?</h1>
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
  <div class="col-sm-6 col-md-6" style="padding:0;">
    <div class="hidden-xs" style="margin-top: 60px;"></div>
    <img class="img-responsive" style="border: 1px solid #ecf0f1;" src="{{BASE_PATH}}/assets/themes/zeppelin/img/notebook.png" />
  </div>
</div>

## Documentation 

#### Quick Start

* [Install](./quickstart/install.html) for basic instructions on installing Apache Zeppelin
* [Explore UI](./quickstart/explore_ui.html): basic components of Apache Zeppelin home
* [Tutorial](./quickstart/tutorial.html)
* [Spark with Zeppelin](./quickstart/spark_with_zeppelin.html)
* [SQL with Zeppelin](./quickstart/sql_with_zeppelin.html)
* [Python with Zeppelin](./quickstart/python_with_zeppelin.html)
  
#### Usage 
* Dynamic Form 
  * [What is Dynamic Form](./usage/dynamic_form/intro.html): a step by step guide for creating dynamic forms
* Display System 
  * [Text Display (`%text`)](./usage/display_system/basic.html#text)
  * [HTML Display (`%html`)](./usage/display_system/basic.html#html)
  * [Table Display (`%table`)](./usage/display_system/basic.html#table)
  * [Angular Display using Backend API (`%angular`)](./usage/display_system/angular_backend.html)
  * [Angular Display using Frontend API (`%angular`)](./usage/display_system/angular_frontend.html)
* Interpreter  
  * [Overview](./usage/interpreter/overview.html): what is interpreter group? how can you set interpreters in Apache Zeppelin?
  * [User Impersonation](./usage/interpreter/user_impersonation.html) when you want to run interpreter as end user
  * [Interpreter Binding Mode](./usage/interpreter/interpreter_binding_mode.html) when you want to manage separate interpreter contexts 
  * [Dependency Management](./usage/interpreter/dependency_management.html) when you include external libraries to interpreter
  * [Installing Interpreters](./usage/interpreter/installation.html): Install not only community managed interpreters but also 3rd party interpreters
  * [Execution Hooks](./usage/interpreter/execution_hooks.html) to specify additional code to be executed by an interpreter at pre and post-paragraph code execution
* Other Features:
  * [Publishing Paragraphs](./usage/other_features/publishing_paragraphs.html) results into your external website
  * [Personalized Mode](./usage/other_features/personalized_mode.html) 
  * [Customizing Zeppelin Homepage](./usage/other_features/customizing_homepage.html) with one of your notebooks
* REST API: available REST API list in Apache Zeppelin
  * [Interpreter API](./usage/rest_api/interpreter.html)
  * [Zeppelin Server API](./usage/rest_api/zeppelin_server.html)
  * [Notebook API](./usage/rest_api/notebook.html)
  * [Notebook Repository API](./usage/rest_api/notebook_repository.html)
  * [Configuration API](./usage/rest_api/configuration.html)
  * [Credential API](./usage/rest_api/credential.html)
  * [Helium API](./usage/rest_api/helium.html)
  
#### Setup 
* Basics 
  * [How to Build Zeppelin](./setup/basics/how_to_build.html)
  * [Multi-user Support](./setup/basics/multi_user_support.html)
* Deployment 
  * [Spark Cluster Mode: Standalone](./setup/deployment/spark_cluster_mode.html#spark-standalone-mode)
  * [Spark Cluster Mode: YARN](./setup/deployment/spark_cluster_mode.html#spark-on-yarn-mode)
  * [Spark Cluster Mode: Mesos](./setup/deployment/spark_cluster_mode.html#spark-on-mesos-mode)
  * [Zeppelin with Flink and Spark Cluster](./setup/deployment/flink_and_spark_cluster.html)
  * [Zeppelin on CDH](./setup/deployment/cdh.html)
  * [Zeppelin on VM: Vagrant](./setup/deployment/virtual_machine.html)
* Security: available security support in Apache Zeppelin
  * [HTTP Basic Auth using NGINX](./setup/security/authentication_nginx.html)
  * [Shiro Authentication](./setup/security/shiro_authentication.html)
  * [Notebook Authorization](./setup/security/notebook_authorization.html)
  * [Data Source Authorization](./setup/security/datasource_authorization.html)
  * [HTTP Security Headers](./setup/security/http_security_headers.html)
* Notebook Storage: a guide about saving notebooks to external storage
  * [Git Storage](./setup/storage/storage.html#notebook-storage-in-local-git-repository)
  * [S3 Storage](./setup/storage/storage.html#notebook-storage-in-s3)
  * [Azure Storage](./setup/storage/storage.html#notebook-storage-in-azure)
  * [ZeppelinHub Storage](./setup/storage/storage.html#notebook-storage-in-zeppelinhub)
  * [MongoDB Storage](./setup/storage/storage.html#notebook-storage-in-mongodb)
* Operation 
  * [Configuration](./setup/operation/configuration.html): lists for Apache Zeppelin
  * [Proxy Setting](./setup/operation/proxy_setting.html)
  * [Upgrading](./setup/operation/upgrading.html): a manual procedure of upgrading Apache Zeppelin version
  * [Trouble Shooting](./setup/operation/trouble_shooting.html)
  
#### Developer Guide
* Extending Zeppelin
  * [Writing Zeppelin Interpreter](./development/writing_zeppelin_interpreter.html)
  * [Helium: Overview](./development/helium/overview.html)
  * [Helium: Writing Application](./development/helium/writing_application.html)
  * [Helium: Writing Spell](./development/helium/writing_spell.html)
  * [Helium: Writing Visualization: Basic](./development/helium/writing_visualization_basic.html)
  * [Helium: Writing Visualization: Transformation](./development/helium/writing_visualization_transformation.html)
* Contributing to Zeppelin
  * [How to Build Zeppelin](./setup/basics/how_to_build.html)
  * [Useful Developer Tools](./development/contribution/useful_developer_tools.html)
  * [How to Contribute (code)](./development/contribution/how_to_contribute_code.html)
  * [How to Contribute (website)](./development/contribution/how_to_contribute_website.html)
  
#### Available Interpreters 
  * [Alluxio](./interpreter/alluxio.html)
  * [Beam](./interpreter/beam.html)
  * [BigQuery](./interpreter/bigquery.html)
  * [Cassandra](./interpreter/cassandra.html)
  * [Elasticsearch](./interpreter/elasticsearch.html)
  * [flink](./interpreter/flink.html)
  * [Geode](./interpreter/geode.html)
  * [Groovy](./interpreter/groovy.html)
  * [HBase](./interpreter/hbase.html)
  * [HDFS](./interpreter/hdfs.html)
  * [Hive](./interpreter/hive.html)
  * [Ignite](./interpreter/ignite.html)
  * [JDBC](./interpreter/jdbc.html)
  * [Kylin](./interpreter/kylin.html)
  * [Lens](./interpreter/lens.html)
  * [Livy](./interpreter/livy.html)
  * [markdown](./interpreter/markdown.html)
  * [Neo4j](./interpreter/neo4j.html)
  * [Pig](./interpreter/pig.html)
  * [Postgresql, HAWQ](./interpreter/postgresql.html)
  * [Python](./interpreter/python.html)
  * [R](./interpreter/r.html)
  * [Scalding](./interpreter/scalding.html)
  * [Scio](./interpreter/scio.html)
  * [Shell](./interpreter/Shell.html)
  * [Spark](./interpreter/spark.html)
  
#### External Resources
  * [Mailing List](https://zeppelin.apache.org/community.html)
  * [Apache Zeppelin Wiki](https://cwiki.apache.org/confluence/display/ZEPPELIN/Zeppelin+Home)
  * [Stackoverflow Questions about Zeppelin (tag: `apache-zeppelin`)](http://stackoverflow.com/questions/tagged/apache-zeppelin)
