---
layout: page
title: "Writing a New Zeppelin Plugin"
description: "Apache Zeppelin Plugin can extend the functionality of the Zeppelin Server."
group: development
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

# Writing a New Zeppelin Plugin

<div id="toc"></div>

## What is Apache Zeppelin Plugin

Apache Zeppelin Plugin can extend the functionality of the Zeppelin zengine. Various extensions can be made to the Zeppelin zengine. For this purpose, an interface must first be implemented in the Zeppelin zengine. The biggest advantage of plugins is the [PluginClassLoader](https://pf4j.org/doc/class-loading.html), which ensures that different versions of the same Java classes do not mix between Zeppelin zengine and the plugins and therefore there are no side effects when running Zeppelin.

## Make your own Zeppelin Plugin

Creating a new plugin is quite simple. Just create a new Maven module and make `zengine-plugins-parent` as parent. Create a class with extends `org.pf4j.Plugin`. Plugins are started during Zeppelin Server Startup.
You can also create classes, which implements `org.pf4j.ExtensionPoint` in Zeppelin zengine.

Current ExtensionPoints:

 - `InterpreterLauncher`
 - `NotebookRepo`

Because Zeppelin does nothing magical you can find also more informations in the [official PF4J documentation](https://pf4j.org/).


