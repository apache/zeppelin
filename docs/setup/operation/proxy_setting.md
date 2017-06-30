---
layout: page
title: "Proxy Setting in Apache Zeppelin"
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

# Proxy Setting 

<div id="toc"></div>

## How to Configure Proxies?

Set `http_proxy` and `https_proxy` env variables. (See [more](https://wiki.archlinux.org/index.php/proxy_settings))

Currently, Proxy is supported only for these features.

- Helium: downloading `helium.json`, installing `npm`, `node`, `yarn`