---
layout: sideMenu
title: "3 - Making A Controller"
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

# How to Write a Controller

<br/>
Our main rule regarding writing a controller, is to use the `Controller as vm` style.

However we also have a few other style guidelines.

You can see below a full example of what the controller would look like.

```
(function() {
  'use strict';

  angular.module('zeppelinWebApp')
    .controller('myNewController', myNewController);

    myNewController.$inject = ['$http'];

    function myNewController($http) {

      var vm = this;

      vm.publicVariable = {};

      // Controller's public functions
      vm.myControllerPublicFunction = myControllerPublicFunction;

      _init();

      /*************************************
      ** Public functions Implementation **
      *************************************/

      function myControllerPublicFunction() {

        ...

      }

      function myOtherPublicFunction() {

        _utilFunction();
        ...

      }

      /*************************************
      ** Private functions Implementation **
      *************************************/

      function _init() {

        vm.myControllerPublicFunction();
        ...

      }

      function _utilFunction() {

        ...

      }

      /***************************************
      ** $scope.$on catchers Implementation **
      ***************************************/

      $scope.$on('eventName', function(event) {

        ...

      })
    }
})();
```

This might look like a lot of lines, but it is mainly to show all the rules at once.


#### Using the controller in a view

Now let's see how we can use it inside our `.html` view in normal situations.

```
<div ng-controller="myNewController as newCtrl">
  <div ng-click="newCtrl.myControllerPublicFunction">{{ newCtrl.publicVariable }}</div>
</div>
```

#### Using the controller in a a page

In the case where your controller will be used on a view directly linked to a route

For example: `localhost:8080/myPageName`

The definition of the controller will take place in the `$routeProvider` section of the `app.js` file.

```
.when('/myPageName', {
  templateUrl: 'app/myPageName/myPageName.html',
  controller: 'myNewController',
  controllerAs: 'newCtrl'
})
```

leaving the `.html` view without any `ng-controller` property.

```
<div>
  <div ng-click="newCtrl.myControllerPublicFunction">{{ newCtrl.publicVariable }}</div>
</div>
```

#### The rules in detail

* Except `$scope.$on` and a few special cases, we do not use the `$scope` of that controller
* Only the functions and variables needed in the view should be linked to the `vm`
* The view is using the controller's variables and functions through the 'as' keyword you set
* Private functions' name start with a `_`
* Functions and variables are grouped by type in that order:
private variables, public variables, init function call, public functions, private functions, $scope.$on functions
* There is a nice comment separating all those groups
* Services are added to the controller using the `$inject` at the top of the component
* Inside the controller, you call the public functions using `vm.nameOfTheFunction()`
