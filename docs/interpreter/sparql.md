---
layout: page
title: "SPARQL Interpreter for Apache Zeppelin"
description: "SPARQL is an RDF query language able to retrieve and manipulate data stored in Resource Description Framework (RDF) format. Apache Zeppelin uses Apache Jena"
group: interpreter
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

# SPARQL Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[SPARQL](https://www.w3.org/TR/sparql11-query/) is an RDF query language able to retrieve and manipulate data stored in Resource Description Framework (RDF) format.
Apache Zeppelin uses [Apache Jena](https://jena.apache.org/) to query SPARQL-Endpoints.

To query your endpoint configure it in the Interpreter-Settings and use the **%sparql** interpreter.
Then write your query in the paragraph.
If you want the prefixes to replace the URI's, set the replaceURIs setting.

## Configuration
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>sparql.endpoint</td>
    <td>http://dbpedia.org/sparql</td>
    <td>Complete URL of the endpoint</td>
  </tr>
  <tr>
    <td>sparql.replaceURIs</td>
    <td>true</td>
    <td>Replace the URIs in the result with the prefixes</td>
  </tr>
</table>

## Example

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/sparql-example.png" width="100%"/>