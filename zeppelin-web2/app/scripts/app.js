'use strict';

function getPort() {
  var port = Number(location.port);
  // brunch port
  if (port === 3333 || port === 9000) {
    port = 8080; 
  }
  return port+1;
}

/**
 * @ngdoc overview
 * @name zeppelinWeb2App
 * @description
 * # zeppelinWeb2App
 *
 * Main module of the application.
 */
angular
  .module('zeppelinWeb2App', [
    'ngAnimate',
    'ngCookies',
    'ngRoute',
    'ngSanitize',
    'angular-websocket',
    'ui.ace',
    'ui.bootstrap',
    'nvd3',
    'ngTouch'
  ])
  .config(function ($routeProvider, WebSocketProvider) {
    WebSocketProvider
      .prefix('')
      .uri('ws://localhost:' + getPort());
      
    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html'
      })
      .when('/notebook/:noteId', {
        templateUrl: 'views/notebooks.html',
        controller: 'NotebookCtrl'
      })
      .otherwise({
        redirectTo: '/'
      });
  });

  
  
