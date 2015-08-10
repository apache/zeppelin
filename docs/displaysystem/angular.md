---
layout: page
title: "Angular Display System"
description: ""
group: display
---
{% include JB/setup %}


### Angular (Beta)

Angular display system treats output as an view template of [AngularJS](https://angularjs.org/).
It compiles templates and display inside of Zeppelin.

Zeppelin provides gateway between your interpreter and your compiled AngularJS view teamplates.
Therefore, you can not only update scope variable from your interpreter  but also watch your scope variable in the interpreter, which is JVM process.

<br />
#### Print AngularJS view

To use angular display system, your output should starts with "%angular".
<img src="../../assets/themes/zeppelin/img/screenshots/display_angular.png" width=600px />

Note that display system is backend independnet.

Because of variable 'name' is not defined, 'Hello \{\{name\}\}' display 'Hello '.

<br />
#### Bind/Unbind variable

Through ZeppelinContext, you can bind/unbind variable to AngularJS view.

Currently it only works in Spark Interpreter (scala).

```
// bind my 'object' as angular scope variable 'name' in current notebook.
z.angularBind(String name, Object object)

// bind my 'object' as angular scope variable 'name' in all notebooks related to current interpreter.
z.angularBindGlobal(String name, Object object)

// unbind angular scope variable 'name' in current notebook.
z.angularBind(String name)

// unbind angular scope variable 'name' in all notebooks related to current interpreter.
z.angularBindGlobal(String name)

```

In the example, let's bind "world" variable 'name'. Then you can see AngularJs view are updated immediately.

<img src="../../assets/themes/zeppelin/img/screenshots/display_angular1.png" width=600px />


<br />
#### Watch/Unwatch variable

Through ZeppelinContext, you can watch/unwatch variable in AngularJs view.

Currently it only works in Spark Interpreter (scala).

```
// register for angular scope variable 'name' (notebook)
z.angularWatch(String name, (before, after) => { ... })

// unregister watcher for angular variable 'name' (notebook)
z.angularUnwatch(String name)

// register for angular scope variable 'name' (global)
z.angularWatchGlobal(String name, (before, after) => { ... })

// unregister watcher for angular variable 'name' (global)
z.angularUnwatchGlobal(String name)


```

Let's make an button, that increment 'run' variable by 1 when it is clicked.
z.angularBind("run", 0) will initialize 'run' to zero. And then register watcher of 'run'.

<img src="../../assets/themes/zeppelin/img/screenshots/display_angular2.png" width=600px />

After clicked button, you'll see both 'run' and numWatched are increased by 1

<img src="../../assets/themes/zeppelin/img/screenshots/display_angular3.png" width=600px />
