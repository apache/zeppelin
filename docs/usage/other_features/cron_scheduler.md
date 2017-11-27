---
layout: page
title: "Running a Notebook on a Given Schedule Automatically"
description: "You can run a notebook on a given schedule automatically by setting up a cron scheduler on the notebook."
group: usage/other_features
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

# Running a Notebook on a Given Schedule Automatically

<div id="toc"></div>

Apache Zeppelin provides a cron scheduler for each notebook. You can run a notebook on a given schedule automatically by setting up a cron scheduler on the notebook.

## Setting up a cron scheduler on a notebook

Click the clock icon on the tool bar and open a cron scheduler dialog box.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/cron_scheduler_dialog_box.png" />

There are the following items which you can input or set:

### Preset

You can set a cron schedule easily by clicking each option such as `1m` and `5m`. The login user is set as a cron executing user automatically. You can also clear the cron schedule settings by clicking `None`.

### Cron expression

You can set the cron schedule by filling in this form. Please see [Cron Trigger Tutorial](http://www.quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/crontrigger) for the available cron syntax.

### Cron executing user

You can set the cron executing user by filling in this form and press the enter key.

### auto-restart interpreter on cron execution

When this checkbox is set to "on", the interpreters which are binded to the notebook are stopped automatically after the cron execution. This feature is useful if you want to release the interpreter resources after the cron execution.

> **Note**: A cron execution is skipped if one of the paragraphs is in a state of `RUNNING` or `PENDING` no matter whether it is executed automatically (i.e. by the cron scheduler) or manually by a user opening this notebook.
