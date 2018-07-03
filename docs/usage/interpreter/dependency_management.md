---
layout: page
title: "Dependency Management for Interpreter"
description: "Include external libraries to Apache Spark Interpreter by setting dependencies in interpreter menu."
group: usage/interpreter 
 
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

## Dependency Management for Interpreter

You can include external libraries to interpreter by setting dependencies in interpreter menu.

When your code requires external library, instead of doing download/copy/restart Zeppelin, you can easily do following jobs in this menu.

 * Load libraries recursively from Maven repository
 * Load libraries from local filesystem
 * Add additional maven repository
 * Automatically add libraries to SparkCluster

<hr>
<div class="row">
  <div class="col-md-6">
    <a data-lightbox="compiler" href="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-dependency-loading.png">
      <img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-dependency-loading.png" />
    </a>
  </div>
  <div class="col-md-6" style="padding-top:30px">
    <b> Load Dependencies to Interpreter </b>
    <br /><br />
    <ol>
      <li> Click 'Interpreter' menu in navigation bar. </li>
      <li> Click 'edit' button of the interpreter which you want to load dependencies to. </li>
      <li> Fill artifact and exclude field to your needs.
           You can enter not only groupId:artifactId:version but also local file in artifact field. </li>
      <li> Press 'Save' to restart the interpreter with loaded libraries. </li>
    </ol>
  </div>
</div>
<hr>
<div class="row">
  <div class="col-md-6">
    <a data-lightbox="compiler" href="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-add-repo1.png">
      <img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-add-repo1.png" />
    </a>
    <a data-lightbox="compiler" href="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-add-repo2.png">
      <img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter-add-repo2.png" />
    </a>
  </div>
  <div class="col-md-6" style="padding-top:30px">
    <b> Add repository for dependency resolving </b>
    <br /><br />
    <ol>
      <li> Press <i class="fa fa-cog"></i> icon in 'Interpreter' menu on the top right side.
           It will show you available repository lists.</li>
      <li> If you need to resolve dependencies from other than central maven repository or
  	   local ~/.m2 repository, hit <i class="fa fa-plus"></i> icon next to repository lists. </li>
      <li> Fill out the form and click 'Add' button, then you will be able to see that new repository is added. </li>
      <li> Optionally, if you are behind a corporate firewall, you can specify also all proxy settings so that Zeppelin can download the dependencies using the given credentials</li>
    </ol>
  </div>
</div>
