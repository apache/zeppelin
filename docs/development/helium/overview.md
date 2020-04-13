---
layout: page
title: "Helium"
description: ""
group: development/helium
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

# Helium Overview

<div id="toc"></div>

## What is Helium? 

Helium is a plugin system that can extend Zeppelin a lot. 
For example, you can write [custom display system](./writing_spell.html) or 
install already published one in [Helium Online Registry](http://zeppelin.apache.org/helium_packages.html). 

Currently, Helium supports 4 types of package.

- [Helium Visualization](./writing_visualization_basic.html): Adding a new chart type
- [Helium Spell](./writing_spell.html): Adding new interpreter, display system running on browser
- [Helium Application](./writing_application.html) 
- [Helium Interpreter](../writing_zeppelin_interpreter.html): Adding a new custom interpreter


## Configuration

Zeppelin ships with several builtin helium plugins which is located in $ZEPPELIN_HOME/heliums. If you want to try more types of heliums plugins,
you can configure `zeppelin.helium.registry` to be `helium,https://s3.amazonaws.com/helium-package/helium.json` in zeppelin-site.xml. `https://s3.amazonaws.com/helium-package/helium.json` will be updated regularly.
