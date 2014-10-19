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

/** get the current port pf the websocket */
function getPort() {
  var port = Number(location.port);
  /** case of binding default port (80 / 443) */
  if (port === 'undifined' || port === 0) {
    port = 80;
    if (location.protocol === 'https:') {
      port = 443;
    }
  }
  // brunch port
  if (port === 3333 || port === 9000) {
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

  
  
