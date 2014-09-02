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
    'nvd3',
    'ngTouch'
  ])
  .config(function ($routeProvider, WebSocketProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html',
        controller: 'MainCtrl'
      })
      .when('/notebooks', {
        templateUrl: 'views/notebooks.html',
        controller: 'NotebookCtrl'
      })
      .otherwise({
        redirectTo: '/'
      });
      
    WebSocketProvider
      .prefix('')
      .uri('ws://localhost:' + getPort());

  });

  
  
