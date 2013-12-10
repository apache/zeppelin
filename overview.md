---
layout: page
title: "Overview"
description: ""
group: nav-right
---
{% include JB/setup %}

### What is Zeppelin

Zeppelin is opensource data analysis environment on top of Hadoop.


### Install

[Download](./download.html) a release.

Zeppelin can be run either locally or with existing hadoop cluster. see [install](./install/install.html)


### Using GUI

Zeppelin has it's own GUI based on web. Browse http://localhost:8080 after start Zeppelin as a daemon by following command

```
bin/zeppelin-daemon.sh start
```

GUI provides
 * ZQL editor
 * Visualization
 * Session save/restore

### Using Command Line Interface

To open interactive CLI session, run

```
bin/zeppelin
```

run with '-h' argument will print available options.


### Play with Zengine

Zengine is java framework for data analytics. check [Zengine](./zengine.html)
