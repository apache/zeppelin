---
layout: page
title: "Multi-user Support"
description: ""
group: setup/basics 
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

# Multi-user Support 

<div id="toc"></div>

This page describes about multi-user support. Zeppelin

- allows multiple users login / logout using [Shiro Authentication](../setup/security/shiro_authentication.html)
- can manage [Notebook Permission](../setup/security/notebook_authorization.html)
- provides [impersonation via interpreters](../../usage/interpreter/user_impersonation.html)
- interpreters have different contexts using [Interpreter Modes]()
- paragraph in a notebook can be [Personalized]() 
- propagates changes in notebooks through websocket in real-time

