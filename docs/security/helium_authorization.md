---
layout: page
title: "Helium Authorization in Apache Zeppelin"
description: "Apache Zeppelin supports Helium plugins which fetch required installer packages from remote registry/repositories"
group: security
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

# Helium Authorization in Apache Zeppelin

<div id="toc"></div>

## How to configure proxies?

Set **http_proxy** and **https_proxy** env variables to allow connection to npm registry behind a corporate firewall.