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
  * The body of this function is just filler. It will be dynamically
  * overridden with the zeppelin-site.xml config value when the client
  * requests the script. If the config value is not defined, it defaults
  * to the HTTP port + 1
  */
function getPort() {
  var port = Number(location.port);
  return port+1;
}

function getWebsocketProtocol() {
  var protocol = 'ws';
  if (location.protocol === 'https:') {
    protocol = 'wss';
  }
  return protocol;
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
    'ngTouch',
    'ngDragDrop'
  ])
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
      .otherwise({
        redirectTo: '/'
      });
  });

  
  
