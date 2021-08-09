---
layout: page
title: "Install"
description: "This page will help you get started and will guide you through installing Apache Zeppelin and running it in the command line."
group: quickstart
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

# Install 

<div id="toc"></div>

Welcome to Apache Zeppelin! On this page are instructions to help you get started.

## Requirements 

Apache Zeppelin officially supports and is tested on the following environments:

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>OpenJDK or Oracle JDK</td>
    <td>1.8 (151+) <br /> (set <code>JAVA_HOME</code>)</td>
  </tr>
  <tr>
    <td>OS</td>
    <td>Mac OSX <br/> Ubuntu 18.04 <br/> Ubuntu 20.04</td>
  </tr>
</table>

### Downloading Binary Package

Two binary packages are available on the [download page](http://zeppelin.apache.org/download.html). Only difference between these two binaries is whether all the interpreters are included in the package file.

- **all interpreter package**: unpack it in a directory of your choice and you're ready to go.
- **net-install interpreter package**: only spark, python, markdown and shell interpreter included. Unpack and follow [install additional interpreters](../usage/interpreter/installation.html) to install other interpreters. If you're unsure, just run `./bin/install-interpreter.sh --all` and install all interpreters.

### Building Zeppelin from source

Follow the instructions [How to Build](../setup/basics/how_to_build.html), If you want to build from source instead of using binary package.

## Starting Apache Zeppelin

#### Starting Apache Zeppelin from the Command Line

On all unix like platforms:

```
bin/zeppelin-daemon.sh start
```

After Zeppelin has started successfully, go to [http://localhost:8080](http://localhost:8080) with your web browser.

By default Zeppelin is listening at `127.0.0.1:8080`, so you can't access it when it is deployed on another remote machine.
To access a remote Zeppelin, you need to change `zeppelin.server.addr` to `0.0.0.0` in `conf/zeppelin-site.xml`.

Check log file at `ZEPPELIN_HOME/logs/zeppelin-server-*.log` if you can not open Zeppelin.

#### Stopping Zeppelin

```
bin/zeppelin-daemon.sh stop
```


## Using the official docker image

Make sure that [docker](https://www.docker.com/community-edition) is installed in your local machine.  

Use this command to launch Apache Zeppelin in a container.

```bash
docker run -p 8080:8080 --rm --name zeppelin apache/zeppelin:0.10.0

```

To persist `logs` and `notebook` directories, use the [volume](https://docs.docker.com/engine/reference/commandline/run/#mount-volume--v-read-only) option for docker container.

```bash
docker run -u $(id -u) -p 8080:8080 --rm -v $PWD/logs:/logs -v $PWD/notebook:/notebook \
           -e ZEPPELIN_LOG_DIR='/logs' -e ZEPPELIN_NOTEBOOK_DIR='/notebook' \
           --name zeppelin apache/zeppelin:0.10.0
```

`-u $(id -u)` is to make sure you have the permission to write logs and notebooks. 

For many interpreters, they require other dependencies, e.g. Spark interpreter requires Spark binary distribution
and Flink interpreter requires Flink binary distribution. You can also mount them via docker volumn. e.g.

```bash
docker run -u $(id -u) -p 8080:8080 --rm -v /mnt/disk1/notebook:/notebook \
-v /usr/lib/spark-current:/opt/spark -v /mnt/disk1/flink-1.12.2:/opt/flink -e FLINK_HOME=/opt/flink  \
-e SPARK_HOME=/opt/spark  -e ZEPPELIN_NOTEBOOK_DIR='/notebook' --name zeppelin apache/zeppelin:0.10.0
```

If you have trouble accessing `localhost:8080` in the browser, Please clear browser cache.


## Start Apache Zeppelin with a service manager

> **Note :** The below description was written based on Ubuntu.

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

```aconf
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

Congratulations, you have successfully installed Apache Zeppelin! Here are a few steps you might find useful:

#### New to Apache Zeppelin...
 * For an in-depth overview, head to [Explore Zeppelin UI](../quickstart/explore_ui.html).
 * And then, try run Tutorial Notebooks shipped with your Zeppelin distribution.
 * And see how to change [configurations](../setup/operation/configuration.html) like port number, etc.

#### Spark, Flink, SQL, Python, R and more 
 * [Spark support in Zeppelin](./spark_with_zeppelin.html), to know more about deep integration with [Apache Spark](http://spark.apache.org/). 
 * [Flink support in Zeppelin](./flink_with_zeppelin.html), to know more about deep integration with [Apache Flink](http://flink.apache.org/).
 * [SQL support in Zeppelin](./sql_with_zeppelin.html) for SQL support
 * [Python support in Zeppelin](./python_with_zeppelin.html), for Matplotlib, Pandas, Conda/Docker integration.
 * [R support in Zeppelin](./r_with_zeppelin.html)
 * [All Available Interpreters](../#available-interpreters)

#### Multi-user support ...
 * Check [Multi-user support](../setup/basics/multi_user_support.html)


