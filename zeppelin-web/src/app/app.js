/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

/** Get the current port of the websocket
  *
  * In the case of running the zeppelin-server normally,
  * the body of this function is just filler. It will be dynamically
  * overridden with the AppScriptServlet from zeppelin-site.xml config value 
  * when the client requests the script.
  *
  * If the config value is not defined, it defaults to the HTTP port + 1
  *
  * At the moment, the key delimiter denoting the end of this function
  * during the replacement is the '}' character.
  *
  * !!!
  * Avoid using '}' inside the function body or you will fail running
  * in server mode.
  * !!!
  *
  * In the case of running "grunt serve", this function will appear
  * as is.
  */

angular
  .module('zeppelinWebApp', [
    'ngAnimate',
    'ngCookies',
    'ngRoute',
    'ngSanitize',
    'angular-websocket',
    'ui.ace',
    'ui.bootstrap',
    'ui.sortable',
    'ngTouch',
    'ngDragDrop',
    'monospaced.elastic',
    'puElasticInput',
    'xeditable'
  ])
  .filter('breakFilter', function() {
    return function (text) {
      if (text !== undefined) {
        return text.replace(/\n/g, '<br />');
      }
    };
  })
  .config(function ($routeProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'app/home/home.html',
        controller: 'HomeCtrl'
      })
      .when('/notebook/:noteId', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl'
      })
      .when('/notebook/:noteId/paragraph/:paragraphId?', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl'
      })
      .when('/interpreter', {
        templateUrl: 'app/interpreter/interpreter.html',
        controller: 'InterpreterCtrl'
      })
      .otherwise({
        redirectTo: '/'
      });
  });
