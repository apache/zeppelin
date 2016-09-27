---
layout: sideMenu
title: "4 - Using ng-bind"
description: ""
group: nav-contrib-front
menu: nav-contrib-front
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

# Performance Gain Using ng-bind

<br/>
We recommend the usage of `ng-bind` in your views.

It allows some performance improvements compared to the usual `{{ "{{ " }}}}` syntax, without adding too much code complexity.

Your code would then look like:

```
<div ng-bing='home.myValue'></div>
```

Instead of:

```
<div>
{{ "{{home.myValue"}}}}
</div>
```

#### Learn More

The topic has been discussed a lot, and you can follow some of these discussions [here](https://github.com/toddmotto/angular-styleguide/issues/41) or
[there](http://stackoverflow.com/questions/27097006/angularjs-why-is-ng-bind-faster-than-expressions).
