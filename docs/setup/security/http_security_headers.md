---
layout: page
title: "Setting up HTTP Response Headers"
description: "There are multiple HTTP Security Headers which can be configured in Apache Zeppelin. This page describes how to enable them by providing appropriate value in Zeppelin configuration file."
group: setup/security
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

# Setting up HTTP Response Headers for Zeppelin 

<div id="toc"></div>

Apache Zeppelin can be configured to include HTTP Headers which aids in preventing Cross Site Scripting (XSS), Cross-Frame Scripting (XFS) and also enforces HTTP Strict Transport Security. Apache Zeppelin also has configuration available to set the Application Server Version to desired value.

## Setting up HTTP Strict Transport Security (HSTS) Response Header

Enabling HSTS Response Header prevents Man-in-the-middle attacks by automatically redirecting HTTP requests to HTTPS when Zeppelin Server is running on SSL. Read on how to configure SSL for Zeppelin [here] (../operation/configuration.html). Even if web page contains any resource which gets served over HTTP or any HTTP links, it will automatically be redirected to HTTPS for the target domain. 
It also prevents MITM attack by not allowing User to override the invalid certificate message, when Attacker presents invalid SSL certificate to the User.  

The following property needs to be updated in the zeppelin-site.xml in order to enable HSTS. You can choose appropriate value for "max-age".

```
<property>
  <name>zeppelin.server.strict.transport</name>
  <value>max-age=631138519</value>
  <description>The HTTP Strict-Transport-Security response header is a security feature that lets a web site tell browsers that it should only be communicated with using HTTPS, instead of using HTTP. Enable this when Zeppelin is running on HTTPS. Value is in Seconds, the default value is equivalent to 20 years.</description>
</property>
```


Possible values are:

* max-age=\<expire-time>
* max-age=\<expire-time>; includeSubDomains
* max-age=\<expire-time>; preload

Read more about HSTS [here](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security).

## Setting up X-XSS-PROTECTION Header

The HTTP X-XSS-Protection response header is a feature of Internet Explorer, Chrome and Safari Web browsers that initiates configured action when they detect reflected cross-site scripting (XSS) attacks.
 
The following property needs to be updated in the zeppelin-site.xml in order to set X-XSS-PROTECTION header. 

```
<property>
  <name>zeppelin.server.xxss.protection</name>
  <value>1; mode=block</value>
  <description>The HTTP X-XSS-Protection response header is a feature of Internet Explorer, Chrome and Safari that stops pages from loading when they detect reflected cross-site scripting (XSS) attacks. When value is set to 1 and a cross-site scripting attack is detected, the browser will sanitize the page (remove the unsafe parts).</description>
</property>
```


You can choose appropriate value from below.

* 0  (Disables XSS filtering)
* 1  (Enables XSS filtering. If a cross-site scripting attack is detected, the browser will sanitize the page.)
* 1; mode=block  (Enables XSS filtering. The browser will prevent rendering of the page if an attack is detected.)

Read more about HTTP X-XSS-Protection response header [here](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-XSS-Protection).

## Setting up X-Frame-Options Header

The X-Frame-Options HTTP response header can indicate browser to avoid clickjacking attacks, by ensuring that their content is not embedded into other sites in a `<frame>`,`<iframe>` or `<object>`.

The following property needs to be updated in the zeppelin-site.xml in order to set X-Frame-Options header.

```
<property>
  <name>zeppelin.server.xframe.options</name>
  <value>SAMEORIGIN</value>
  <description>The X-Frame-Options HTTP response header can be used to indicate whether or not a browser should be allowed to render a page in a frame/iframe/object.</description>
</property>
```


You can choose appropriate value from below.

* DENY
* SAMEORIGIN
* ALLOW-FROM _uri_

## Setting up Server Header

Security conscious organisations does not want to reveal the Application Server name and version to prevent finding this information easily by Attacker while fingerprinting the Application. The exact version number can tell an Attacker if the current Application Server is patched for or vulnerable to certain publicly known CVE associated to it.

The following property needs to be updated in the zeppelin-site.xml in order to set Server header.

```
<property>
    <name>zeppelin.server.jetty.name</name>
    <value>Jetty(7.6.0.v20120127)</value>
    <description>Hardcoding Application Server name to Prevent Fingerprinting</description>
</property>
```

The value can be any "String".