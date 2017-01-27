---
layout: page
title: "Customize Apache Zeppelin homepage"
description: "Apache Zeppelin allows you to use one of the notes you create as your Zeppelin Homepage. With that you can brand your Zeppelin installation, adjust the instruction to your users needs and even translate to other languages."
group: manual
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

# Customize Apache Zeppelin homepage

<div id="toc"></div>

Apache Zeppelin allows you to use one of the notes you create as your Zeppelin Homepage.
With that you can brand your Zeppelin installation, adjust the instruction to your users needs and even translate to other languages.

## How to set a note as your Zeppelin homepage

The process for creating your homepage is very simple as shown below:

1. Create a note using Zeppelin
2. Set the note id in the config file
3. Restart Zeppelin

### Create a note using Zeppelin
Create a new note using Zeppelin,
you can use ```%md``` interpreter for markdown content or any other interpreter you like.
You can also use the display system to generate [text](../displaysystem/basicdisplaysystem.html#text), [html](../displaysystem/basicdisplaysystem.html#html), [table](../displaysystem/basicdisplaysystem.html#table) or
Angular ([backend API](../displaysystem/back-end-angular.html), [frontend API](../displaysystem/front-end-angular.html)).

Run (shift+Enter) the note and see the output. Optionally, change the note view to report to hide
the code sections.

### Set the note id in the config file
To set the note id in the config file, you should copy it from the last word in the note url.
For example,

<img src="/assets/themes/zeppelin/img/screenshots/homepage_notebook_id.png" width="400px" />

Set the note id to the ```ZEPPELIN_NOTEBOOK_HOMESCREEN``` environment variable
or ```zeppelin.notebook.homescreen``` property.

You can also set the ```ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE``` environment variable
or ```zeppelin.notebook.homescreen.hide``` property to hide the new note from the note list.

### Restart Zeppelin
Restart your Zeppelin server

```
./bin/zeppelin-daemon stop
./bin/zeppelin-daemon start
```
That's it! Open your browser and navigate to Apache Zeppelin and see your customized homepage.

<br />
## Show notes list in your custom homepage
If you want to display the list of notes on your custom Apache Zeppelin homepage all
you need to do is use our %angular support.

Add the following code to a paragraph in your Apache Zeppelin note and run it.

```javascript
println(
"""%angular
  <div ng-include="'app/home/notebook.html'"></div>
""")
```

After running the paragraph, you will see output similar to this one:

<img src="/assets/themes/zeppelin/img/screenshots/homepage_custom_notebook_list.png" />

That's it! Voila! You have your notes list.


