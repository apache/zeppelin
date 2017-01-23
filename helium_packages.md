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

From Zeppelin-0.7, you can load/unload a pluggable Apache Zeppelin Visualization package on runtime through [Helium framework](https://issues.apache.org/jira/browse/ZEPPELIN-533) in Zeppelin.
Since it's a npm packge, surely can be published to [npm registry](https://www.npmjs.com/). 
Here are the lists of Helium packages registered in the registry. 
If you need more information about how you can use the below packages in Zeppelin, see [How it works](https://zeppelin.apache.org/docs/latest/development/writingzeppelinvisualization.html#how-it-works).
Or you can also create your own package as described in [Write new Visualization](https://zeppelin.apache.org/docs/latest/development/writingzeppelinvisualization.html#write-new-visualization) section.
<br />
<div ng-app="app">
  <div ng-controller="HeliumPkgCtrl">
    <div class="box width-full heliumPackageContainer">
      <p>{% raw %}{{HeliumPkgs.length}}{% endraw %} packages found
      </p>
      <div class="row heliumPackageList"
           ng-repeat="pkg in latestPkgInfo | orderBy: 'name'">
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
