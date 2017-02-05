---
layout: page
title: "Helium Visualization Packages"
description: "A list of VISUALIZATION type of Helium packages published in npm registry: https://www.npmjs.com/"
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

# Helium Packages

From Zeppelin-0.7, you can load/unload a pluggable Apache Zeppelin package on runtime through [Helium framework](https://issues.apache.org/jira/browse/ZEPPELIN-533) in Zeppelin.
Since it's a [npm package](https://docs.npmjs.com/getting-started/what-is-npm), surely can be published to [npm registry](https://docs.npmjs.com/misc/registry). 
Here are the lists of Helium packages registered in the registry. 
If you need more information about how you can use the below packages in Zeppelin, see [How it works](https://zeppelin.apache.org/docs/latest/development/writingzeppelinvisualization.html#how-it-works).
Or you can also create your own package as described in [Write new Visualization](https://zeppelin.apache.org/docs/latest/development/writingzeppelinvisualization.html#write-new-visualization) section.
<br />
<div ng-app="app">
  <div ng-controller="HeliumPkgCtrl">
    <div class="box width-full heliumPackageContainer">
      <p>List by</p>
      <form ng-init="content='all'">
        <input class="helium-radio" id="all" type="radio" name="content" ng-model="content" value="all"><label for="all">Lately published</label>
        <input class="helium-radio" id="viz" type="radio" name="content" ng-model="content" value="viz"><label for="viz">Type: Visualization</label>
        <input class="helium-radio" id="spell" type="radio" name="content" ng-model="content" value="spell">
        <label for="spell">Type: Spell
          <span style="color: gray; font-style: italic; font-size: 11px;">only available in development version(0.8.0-SNAPSHOT)</span>
          </label>
      </form>
      <br />
      <p ng-show="content == 'all'">{% raw %}{{latestPkgInfo.length}}{% endraw %} package(s) registered</p>
      <p ng-show="content == 'spell'">{% raw %}{{spellTypePkgs.length}}{% endraw %} package(s) registered</p>
      <p ng-show="content == 'viz'">{% raw %}{{vizTypePkgs.length}}{% endraw %} package(s) registered</p>
      <div class="row heliumPackageList"
           ng-repeat="pkg in latestPkgInfo | orderBy: ['published', 'type']:true"
           ng-show="content == 'all'">
        <div class="col-md-12">
          <div class="heliumPackageHead">
            <div class="heliumPackageIcon"
                 ng-bind-html="pkg.icon"></div>
            <div class="heliumPackageName">
              <a ng-href="{% raw %}{{npmWebLink}}/{{pkg.name}}{% endraw %}"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <span>{% raw %}{{pkg.type}}{% endraw %}</span>
            </div>
          </div>
          <div class="heliumPackageAuthor">
            by {% raw %}{{pkg.author}}{% endraw %}
          </div>
          <div class="heliumPackageDescription">
            {% raw %}{{pkg.description}}{% endraw %}
          </div>
          <div class="heliumPackageLatestVersion">
              v {% raw %}{{pkg.artifact.split('@')[1]}}{% endraw %}
          </div>
        </div>
      </div>
      <div class="row heliumPackageList"
           ng-repeat="pkg in spellTypePkgs | orderBy: 'name'"
           ng-show="content == 'spell'">
        <div class="col-md-12">
          <div class="heliumPackageHead">
            <div class="heliumPackageIcon"
                 ng-bind-html="pkg.icon"></div>
            <div class="heliumPackageName">
              <a ng-href="{% raw %}{{npmWebLink}}/{{pkg.name}}{% endraw %}"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <span>{% raw %}{{pkg.type}}{% endraw %}</span>
            </div>
          </div>
          <div class="heliumPackageAuthor">
            by {% raw %}{{pkg.author}}{% endraw %}
          </div>
          <div class="heliumPackageDescription">
            {% raw %}{{pkg.description}}{% endraw %}
          </div>
          <div class="heliumPackageLatestVersion">
              v {% raw %}{{pkg.artifact.split('@')[1]}}{% endraw %}
          </div>
        </div>
      </div>
      <div class="row heliumPackageList"
           ng-repeat="pkg in vizTypePkgs | orderBy: 'name'"
           ng-show="content == 'viz'">
        <div class="col-md-12">
          <div class="heliumPackageHead">
            <div class="heliumPackageIcon"
                 ng-bind-html="pkg.icon"></div>
            <div class="heliumPackageName">
              <a ng-href="{% raw %}{{npmWebLink}}/{{pkg.name}}{% endraw %}"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <span>{% raw %}{{pkg.type}}{% endraw %}</span>
            </div>
          </div>
          <div class="heliumPackageAuthor">
            by {% raw %}{{pkg.author}}{% endraw %}
          </div>
          <div class="heliumPackageDescription">
            {% raw %}{{pkg.description}}{% endraw %}
          </div>
          <div class="heliumPackageLatestVersion">
              v {% raw %}{{pkg.artifact.split('@')[1]}}{% endraw %}
          </div>
        </div>
      </div>
    </div>
  </div>  
</div>
