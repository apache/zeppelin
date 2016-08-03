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

<div id="home-search" class="home">

<span id="search">
  <p><i class="glyphicon glyphicon-search"></i> &nbsp;Search Docs</p>
</span>
<br/>

<form role="search" action="{{BASE_PATH}}/search.html" id="site_search">
  <div class="input-group" id="search-container">
    <input type="text" class="form-control" size="16px" name="q" placeholder="Search all pages" id="search_box">
    <span class="input-group-btn">
      <button type="reset" class="btn btn-default">
        <i class="glyphicon glyphicon-remove" style="color:#777"></i>
      </button>
    </span>
  </div>
</form>

<br/><br/>

<div id="search_results"></div>
</div>
