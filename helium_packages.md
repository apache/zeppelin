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
Here are the lists of Helium packages registered in the [NPM Registry](https://docs.npmjs.com/misc/registry) and [Maven Central Repository](http://central.sonatype.org/).
If you need more information about how you can use the below packages in Zeppelin, see [What is Apache Zeppelin Visualization](https://zeppelin.apache.org/docs/latest/development/writingzeppelinvisualization.html)
and [What is Apache Zeppelin Spell](https://zeppelin.apache.org/docs/snapshot/development/writingzeppelinspell.html).
<br />
<div ng-app="app">
  <div ng-controller="HeliumPkgCtrl">
    <div class="box width-full heliumPackageContainer">
      <p>List by</p>
      <form>
        <div>
          <input class="helium-radio" id="'ALL'" type="radio" 
               ng-model="pkgListByType" ng-value="'ALL'" ng-click="pkgListByType = 'ALL'">
          <label for="'ALL'">Recently published</label>
        </div>
        <div ng-repeat="pkgTypes in allPackageTypes">
          <input class="helium-radio" id="{% raw %}{{pkgTypes}}{% endraw %}" type="radio" 
                 ng-model="$parent.pkgListByType" ng-value="pkgTypes" ng-click="$parent.pkgListByType = pkgTypes">
          <label for="{% raw %}{{pkgTypes}}{% endraw %}">Type: {% raw %}{{pkgTypes}}{% endraw %}
          <span  ng-if="pkgTypes === 'SPELL'" 
                style="color: gray; font-style: italic; font-size: 11px;">
            only available in development version(0.8.0-SNAPSHOT)
          </span>
          </label>
        </div>
      </form>
      <br />
      <div>
        <p ng-if="pkgListByType === 'ALL'">
          {% raw %}{{latestPkgInfo.length}}{% endraw %} package(s) registered
        </p>
      </div>
      <div ng-repeat="(types, pkgs) in allTypePkgs">
        <p ng-show="$parent.pkgListByType === types"">
          {% raw %}{{pkgs.length}}{% endraw %} package(s) registered
        </p>
      </div>
      <div ng-if="pkgListByType === 'ALL'">
        <div class="row heliumPackageList"
             ng-repeat="pkg in latestPkgInfo.slice(((currentPage-1)*itemsPerPage), ((currentPage)*itemsPerPage))">
        <div class="col-md-12">
          <div class="heliumPackageHead">
            <div class="heliumPackageIcon"
                 ng-if="pkg.type !== 'INTERPRETER'"
                 ng-bind-html="pkg.icon"></div>
            <div class="heliumPackageIcon"
                 ng-if="pkg.type === 'INTERPRETER'"
                 ng-bind-html="intpDefaultIcon"></div>
            <div class="heliumPackageName">
              <a ng-if="pkg.type !== 'INTERPRETER'"
                 ng-href="https://www.npmjs.com/package/{% raw %}{{pkg.name}}{% endraw %}"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <a ng-if="pkg.type === 'INTERPRETER'"
                 ng-href="http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22{% raw %}{{pkg.artifactId}}{% endraw %}%22%20AND%20v%3A%22{% raw %}{{pkg.version}}{% endraw %}%22"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <span>{% raw %}{{pkg.type}}{% endraw %}</span>
            </div>
          </div>
          <div class="heliumPackageAuthor"
               ng-if="pkg.type !== 'INTERPRETER'">
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
      <div class="text-center" style="margin-top: 24px;">
        <ul uib-pagination boundary-links="true" total-items="latestPkgInfo.length" 
            ng-model="currentPage" class="pagination-sm"
            max-size="maxSize"
            items-per-page="itemsPerPage"
            previous-text="&lsaquo;" next-text="&rsaquo;" first-text="&laquo;" last-text="&raquo;"></ul>
      </div>
    </div>
    <div ng-if="pkgListByType !== 'ALL'" ng-repeat="(types, pkgs) in allTypePkgs">
        <div class="row heliumPackageList"
           ng-repeat="pkg in pkgs.slice(((currentPage-1)*itemsPerPage), ((currentPage)*itemsPerPage))"
           ng-show="$parent.pkgListByType === types">
        <div class="col-md-12">
          <div class="heliumPackageHead">
            <div class="heliumPackageIcon"
                 ng-if="pkg.type !== 'INTERPRETER'"
                 ng-bind-html="pkg.icon"></div>
            <div class="heliumPackageIcon"
                 ng-if="pkg.type === 'INTERPRETER'"
                 ng-bind-html="intpDefaultIcon"></div>
            <div class="heliumPackageName">
              <a ng-if="pkg.type !== 'INTERPRETER'"
                 ng-href="https://www.npmjs.com/package/{% raw %}{{pkg.name}}{% endraw %}"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <a ng-if="pkg.type === 'INTERPRETER'"
                 ng-href="http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22{% raw %}{{pkg.artifactId}}{% endraw %}%22%20AND%20v%3A%22{% raw %}{{pkg.version}}{% endraw %}%22"
                 target="_blank">
                 {% raw %}{{pkg.name}}{% endraw %}
              </a>
              <span>{% raw %}{{pkg.type}}{% endraw %}</span>
            </div>
          </div>
          <div class="heliumPackageAuthor"
               ng-if="pkg.type !== 'INTERPRETER'">
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
      <div class="text-center" style="margin-top: 24px;">
        <ul uib-pagination boundary-links="true" total-items="pkgs.length" 
            ng-model="$parent.currentPage" class="pagination-sm"
            ng-show="$parent.pkgListByType === types"
            max-size="maxSize"
            items-per-page="itemsPerPage"
            previous-text="&lsaquo;" next-text="&rsaquo;" first-text="&laquo;" last-text="&raquo;"></ul>
      </div>
    </div>
    </div>
  </div>  
</div>
