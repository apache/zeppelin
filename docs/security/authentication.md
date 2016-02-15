---
layout: page
title: "Authentication"
description: "Authentication"
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
# Authentication

Authentication is company-specific. 

One option is to use [Basic Access Authentication](https://en.wikipedia.org/wiki/Basic_access_authentication)
 
Anoteher is to have an authentication server that can verify user credentials in an LDAP server.
If an incoming request to the Zeppelin server does not have a cookie with user information encrypted with the authentication server public key, the user
is redirected to the authentication server. Once the user is verified, the authentication server redirects the browser to a specific 
URL in the Zeppelin server which sets the authentication cookie in the browser. 
The end result is that all requests to the Zeppelin
web server have the authentication cookie which contains user and groups information.
