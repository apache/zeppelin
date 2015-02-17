/* Copyright 2014 NFlabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

/** get the current port of the websocket
  * In the case of running the zeppelin-server normally,
  * The body of this function is just filler. It will be dynamically
  * overridden with the zeppelin-site.xml config value when the client
  * requests the script. If the config value is not defined, it defaults
  * to the HTTP port + 1
  *
  * At the moment, the key delimiter denoting the end of this function
  * during the replacement is the '}' character. Avoid using this
  * character inside the function body
  *
  * In the case of running "grunt serve", this function will appear
  * as is.
  */
function getPort() {
  var port = Number(location.port);
  if (location.protocol !== 'https:' && (port === 'undifined' || port === 0)) {
    port = 80;
  } else if (location.protocol === 'https:' && (port === 'undifined' || port === 0)) {
    port = 443;
  } else if (port === 3333 || port === 9000) {
    port = 8080;
  }
  return port+1;
}

function getWebsocketProtocol() {
  var protocol = 'ws';
  if (location.protocol === 'https:') {
    protocol = 'wss';
  }
  return protocol;
}

function getRestApiBase() {
  var port = Number(location.port);
  if (port === 'undefined' || port === 0) {
    port = 80;
    if (location.protocol === 'https:') {
      port = 443;
    }
  }

  if (port === 3333 || port === 9000) {
    port = 8080;
  }
  return location.protocol+"//"+location.hostname+":"+port+"/api";
}

/**
 * @ngdoc overview
 * @name zeppelinWebApp
 * @description
 * # zeppelinWebApp
 *
 * Main module of the application.
 *
 * @author anthonycorbacho
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
      if (text !== undefined) return text.replace(/\n/g, '<br />');
    };
  })
  .config(function ($routeProvider, WebSocketProvider) {
    WebSocketProvider
      .prefix('')
      .uri(getWebsocketProtocol() + '://' + location.hostname + ':' + getPort());

    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html'
      })
      .when('/notebook/:noteId', {
        templateUrl: 'views/notebooks.html',
        controller: 'NotebookCtrl'
      })
      .when('/notebook/:noteId/paragraph/:paragraphId?', {
        templateUrl: 'views/notebooks.html',
        controller: 'NotebookCtrl'
      })
      .when('/interpreter', {
        templateUrl: 'views/interpreter.html',
        controller: 'InterpreterCtrl'
      })
      .otherwise({
        redirectTo: '/'
      });
  });



