---
layout: sideMenu
title: "2 - Event Dispatching"
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

# Event Dispatching in Angular

<br/>
AngularJS provides an Event dispatching system allowing the communication between controllers or services and controllers.

`$broadcast` dispatches the event downwards through the child scopes

`$emit` dispatches the event upwards through the scope hierarchy

`$on` catches the event passing by that scope

<br/>
You can usually see it being used from `$rootScope` or `$scope`

```
$scope.$broadcast('eventName', arg);
$rootScope.$broadcast('eventName', arg);

$scope.$emit('eventName', arg);
$rootScope.$emit('eventName', arg);

$scope.$on('eventToCatch', function);
$rootScope.$on('eventToCatch', function);
```

Now, there are a few things to know about using it from `$rootScope`:

* Both `$rootScope.$emit` and `$rootScope.$broadcast` go through child scopes since `$rootScope` doesn't have a parent
* `$rootScope.$emit` can only be received by `$rootScope.$on`
* `$rootScope.$broadcast` can be received by `$rootScope.$on` and `$scope.$on`
* `$rootScope.$on` listener needs to be removed by hand (Memory leak if forgotten)


#### How we are using it in the project

* Usage of event dispatching should be limited if possible
* We only use `$rootScope.$broadcast` and `$scope.$on` for event dispatching/catching
* We are grouping all the `$scope.$on` functions at the end of the compponent using it (controller or service)


#### Performances

Using `$broadcast` might not seem optimum if we consider the description we have above.

However, it is optimized to only go through branches that have a matching event binding.
(cf. [this post](http://www.bennadel.com/blog/2724-scope-broadcast-is-surprisingly-efficient-in-angularjs.htm))
