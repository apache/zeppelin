---
layout: sideMenu
title: "1 - Defining Components"
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

# Defining Angular Components

<br/>
We should have only one Angular Component per file, and it should look like this:

```
'use strict';
(function() {

  angular.module('zeppelinWebApp').controller('HomeCtrl', HomeCtrl);

  function HomeCtrl($location) {
  'ngInject';
    ...
  }

})();
```

#### Explanations

* The component function and the component's dependency injection are separated from the component definition
* We apply an Immediately Invoked Function Expression (IIFE) to each component, You can learn more about it
in this [nice post](https://github.com/johnpapa/angular-styleguide/tree/master/a1#iife) of John Papa.
