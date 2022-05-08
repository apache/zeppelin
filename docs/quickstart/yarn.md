---
layout: page
title: "Zeppelin on Yarn"
description: "Apache Zeppelin supports to run interpreter process in yarn containers"
group: usage/interpreter 
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

# Zeppelin Interpreter on Yarn

<div id="toc"></div>

Zeppelin is able to run interpreter process in yarn container. The key benefit is the scalability, you won't run out of memory
of the zeppelin server host if you run large amount of interpreter processes.

## Prerequisites
The following is required for yarn interpreter mode.

* Hadoop client (both 2.x and 3.x are supported) is installed.
* `$HADOOP_HOME/bin` is put in `PATH`. Because internally zeppelin will run command `hadoop classpath` to get all the hadoop jars and put them in the classpath of Zeppelin.
* Set `USE_HADOOP` as `true` in `zeppelin-env.sh`.

## Configuration

Yarn interpreter mode needs to be set for each interpreter. You can set `zeppelin.interpreter.launcher` to be `yarn` to run it in yarn mode.
Besides that, you can also specify other properties as following table.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Default Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.interpreter.yarn.resource.memory</td>
    <td>1024</td>
    <td>memory for interpreter process, unit: mb</td>
  </tr>
  <tr>
    <td>zeppelin.interpreter.yarn.resource.memoryOverhead</td>
    <td>384</td>
    <td>Amount of non-heap memory to be allocated per interpreter process in yarn interpreter mode, in MiB unless otherwise specified. This is memory that accounts for things like VM overheads, interned strings, other native overheads, etc.</td>
  </tr>
  <tr>
    <td>zeppelin.interpreter.yarn.resource.cores</td>
    <td>1</td>
    <td>cpu cores for interpreter process</td>
  </tr>
  <tr>
    <td>zeppelin.interpreter.yarn.queue</td>
    <td>default</td>
    <td>yarn queue name</td>
  </tr>
  <tr>
    <td>zeppelin.interpreter.yarn.node.label.expression</td>
    <td></td>
    <td>yarn node label expression specified for interpreter process</td>
  </tr>
</table>

## Differences with non-yarn interpreter mode (local mode)

There're several differences between yarn interpreter mode with non-yarn interpreter mode (local mode)

* New yarn app will be allocated for the interpreter process.
* Any local path setting won't work in yarn interpreter process. E.g. if you run python interpreter in yarn interpreter mode, then you need to make sure the python executable of `zeppelin.python` exist in all the nodes of yarn cluster. 
Because the python interpreter may launch in any node.
* Don't use it for spark interpreter. Instead use spark's built-in yarn-client or yarn-cluster which is more suitable for spark interpreter.
