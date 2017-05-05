---
layout: page
title: "Quick Start"
description: "This page will help you get started and will guide you through installing Apache Zeppelin and running it in the command line."
group: install
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

# Quick Start

<div id="toc"></div>

Welcome to Apache Zeppelin! On this page are instructions to help you get started.

## Installation

Apache Zeppelin officially supports and is tested on the following environments:

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>Oracle JDK</td>
    <td>1.7 <br /> (set <code>JAVA_HOME</code>)</td>
  </tr>
  <tr>
    <td>OS</td>
    <td>Mac OSX <br /> Ubuntu 14.X <br /> CentOS 6.X <br /> Windows 7 Pro SP1</td>
  </tr>
</table>

### Downloading Binary Package

Two binary packages are available on the [Apache Zeppelin Download Page](http://zeppelin.apache.org/download.html). Only difference between these two binaries is interpreters are included in the package file.

- #### Package with `all` interpreters.

  Just unpack it in a directory of your choice and you're ready to go.

- #### Package with `net-install` interpreters.

  Unpack and follow [install additional interpreters](../manual/interpreterinstallation.html) to install interpreters. If you're unsure, just run `./bin/install-interpreter.sh --all` and install all interpreters.

### Additional Installation Requirements ###

On Windows the Zeppelin Spark interpreter may require the additional installation of the Microsoft Visual C++ Runtime (specifically mvscr100.dll). You may download the corresponding runtime from https://www.microsoft.com/en-US/download/details.aspx?id=14632 (x64) or https://www.microsoft.com/en-US/download/details.aspx?id=5555 (x86).

## Starting Apache Zeppelin

#### Starting Apache Zeppelin from the Command Line

On all unix like platforms:

```
bin/zeppelin-daemon.sh start
```

If you are on Windows:

```
bin\zeppelin.cmd
```

After Zeppelin has started successfully, go to [http://localhost:8080](http://localhost:8080) with your web browser.

#### Stopping Zeppelin

```
bin/zeppelin-daemon.sh stop
```

#### Start Apache Zeppelin with a service manager

> **Note :** The below description was written based on Ubuntu Linux.

Apache Zeppelin can be auto-started as a service with an init script, using a service manager like **upstart**.

This is an example upstart script saved as `/etc/init/zeppelin.conf`
This allows the service to be managed with commands such as

```
sudo service zeppelin start  
sudo service zeppelin stop  
sudo service zeppelin restart
```

Other service managers could use a similar approach with the `upstart` argument passed to the `zeppelin-daemon.sh` script.

```
bin/zeppelin-daemon.sh upstart
```

**zeppelin.conf**

```
description "zeppelin"

start on (local-filesystems and net-device-up IFACE!=lo)
stop on shutdown

# Respawn the process on unexpected termination
respawn

# respawn the job up to 7 times within a 5 second period.
# If the job exceeds these values, it will be stopped and marked as failed.
respawn limit 7 5

# zeppelin was installed in /usr/share/zeppelin in this example
chdir /usr/share/zeppelin
exec bin/zeppelin-daemon.sh upstart
```


## Next Steps

Congratulations, you have successfully installed Apache Zeppelin! Here are few steps you might find useful:

#### New to Apache Zeppelin...
 * For an in-depth overview, head to [Explore Apache Zeppelin UI](../quickstart/explorezeppelinui.html).
 * And then, try run [tutorial](http://localhost:8080/#/notebook/2A94M5J1Z) notebook in your Zeppelin.
 * And see how to change [configurations](./configuration.html) like port number, etc.

#### Zeppelin with Apache Spark ...
 * To know more about deep integration with [Apache Spark](http://spark.apache.org/), check [Spark Interpreter](../interpreter/spark.html).

#### Zeppelin with JDBC data sources ...
 * Check [JDBC Interpreter](../interpreter/jdbc.html) to know more about configure and uses multiple JDBC data sources.

#### Zeppelin with Python ...
 * Check [Python interpreter](../interpreter/python.html) to know more about Matplotlib, Pandas, Conda/Docker environment integration.


#### Multi-user environment ...
 * Turn on [authentication](../security/shiroauthentication.html).
 * Manage your [notebook permission](../security/notebook_authorization.html).
 * For more informations, go to **More** -> **Security** section.

#### Other useful informations ...
 * Learn how [Display System](../displaysystem/basicdisplaysystem.html) works.
 * Use [Service Manager](#start-apache-zeppelin-with-a-service-manager) to start Zeppelin.
 * If you're using previous version please see [Upgrade Zeppelin version](./upgrade.html).


## Building Apache Zeppelin from Source

If you want to build from source instead of using binary package, follow the instructions [here](./build.html).

