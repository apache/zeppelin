---
layout: page
title: "Notebook as Homepage"
description: ""
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

## Customize your zeppelin homepage
 Zeppelin allows you to use one of the notebooks you create as your zeppelin Homepage.
 With that you can brand your zeppelin installation, 
 adjust the instruction to your users needs and even translate to other languages.

 <br />
### How to set a notebook as your zeppelin homepage

The process for creating your homepage is very simple as shown below:
 
 1. Create a notebook using zeppelin
 2. Set the notebook id in the config file
 3. Restart zeppelin
 
 <br />
#### Create a notebook using zeppelin
  Create a new notebook using zeppelin,
  you can use ```%md``` interpreter for markdown content or any other interpreter you like.
  
  You can also use the display system to generate [text](../displaysystem/display.html), 
  [html](../displaysystem/display.html#html),[table](../displaysystem/table.html) or
   [angular](../displaysystem/angular.html)

   Run (shift+Enter) the notebook and see the output. Optionally, change the notebook view to report to hide 
   the code sections.
     
   <br />
#### Set the notebook id in the config file
  To set the notebook id in the config file you should copy it from the last word in the notebook url 
  
  for example
  
  <img src="/assets/themes/zeppelin/img/screenshots/homepage_notebook_id.png" />

  Set the notebook id to the ```ZEPPELIN_NOTEBOOK_HOMESCREEN``` environment variable 
  or ```zeppelin.notebook.homescreen``` property. 
  
  You can also set the ```ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE``` environment variable 
  or ```zeppelin.notebook.homescreen.hide``` property to hide the new notebook from the notebook list.

  <br />
#### Restart zeppelin
  Restart your zeppelin server
  
  ```
  ./bin/zeppelin-deamon stop 
  ./bin/zeppelin-deamon start
  ```
  ####That's it! Open your browser and navigate to zeppelin and see your customized homepage...
    
  
<br />
### Show notebooks list in your custom homepage
If you want to display the list of notebooks on your custom zeppelin homepage all 
you need to do is use our %angular support.
  
  <br />
  Add the following code to a paragraph in you home page and run it... walla! you have your notebooks list.
  
  ```javascript
  println(
  """%angular 
    <div class="col-md-4" ng-controller="HomeCtrl as home">
      <h4>Notebooks</h4>
      <div>
        <h5><a href="" data-toggle="modal" data-target="#noteNameModal" style="text-decoration: none;">
          <i style="font-size: 15px;" class="icon-notebook"></i> Create new note</a></h5>
          <ul style="list-style-type: none;">
            <li ng-repeat="note in home.notes.list track by $index"><i style="font-size: 10px;" class="icon-doc"></i>
              <a style="text-decoration: none;" href="#/notebook/{{note.id}}">{{note.name || 'Note ' + note.id}}</a>
            </li>
          </ul>
      </div>
    </div>
  """)
  ```
  
  After running the notebook you will see output similar to this one:
  <img src="/assets/themes/zeppelin/img/screenshots/homepage_notebook_list.png" />
  
  The main trick here relays in linking the ```<div>``` to the controller:
  
  ```javascript
  <div class="col-md-4" ng-controller="HomeCtrl as home">
  ```
  
  Once we have ```home``` as our controller variable in our ```<div></div>``` 
  we can use ```home.notes.list``` to get access to the notebook list.