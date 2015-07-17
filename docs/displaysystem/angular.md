---
layout: page
title: "Angular Display System"
description: ""
group: display
---
{% include JB/setup %}


### Angular

Angular display system treats output an a AngularJs's view template.
It compiles templates and display inside of notebook.

Zeppelin provides gateway between interpreter process (JVM) and your compiled AngularJS view teamplates through ZeppelinContext.

Therefore, you can not only update scope variable from your interpreter  but also watch your scope variable in the interpreter, which is JVM process.

For more information about the AngularJS gateway, please check ZeppelinContext.
