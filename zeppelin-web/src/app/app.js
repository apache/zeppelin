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
(function() {
  var zeppelinWebApp = angular.module('zeppelinWebApp', [
          'ngCookies',
          'ngAnimate',
          'ngRoute',
          'ngSanitize',
          'angular-websocket',
          'ui.ace',
          'ui.bootstrap',
          'as.sortable',
          'ngTouch',
          'ngDragDrop',
          'angular.filter',
          'monospaced.elastic',
          'puElasticInput',
          'xeditable',
          'ngToast',
          'focus-if',
          'ngResource',
          'ngclipboard'
      ])
        .filter('breakFilter', function() {
          return function(text) {
            if (!!text) {
              return text.replace(/\n/g, '<br />');
            }
          };
        })
        .config(function($httpProvider, $routeProvider, ngToastProvider) {
          // withCredentials when running locally via grunt
          $httpProvider.defaults.withCredentials = true;

          $routeProvider
            .when('/', {
              templateUrl: 'app/home/home.html'
            })
            .when('/notebook/:noteId', {
              templateUrl: 'app/notebook/notebook.html',
              controller: 'NotebookCtrl'
            })
            .when('/notebook/:noteId/paragraph?=:paragraphId', {
              templateUrl: 'app/notebook/notebook.html',
              controller: 'NotebookCtrl'
            })
            .when('/notebook/:noteId/paragraph/:paragraphId?', {
              templateUrl: 'app/notebook/notebook.html',
              controller: 'NotebookCtrl'
            })
            .when('/notebook/:noteId/revision/:revisionId', {
              templateUrl: 'app/notebook/notebook.html',
              controller: 'NotebookCtrl'
            })
            .when('/jobmanager', {
              templateUrl: 'app/jobmanager/jobmanager.html',
              controller: 'JobmanagerCtrl'
            })
            .when('/interpreter', {
              templateUrl: 'app/interpreter/interpreter.html',
              controller: 'InterpreterCtrl'
            })
            .when('/notebookRepos', {
              templateUrl: 'app/notebookRepos/notebookRepos.html',
              controller: 'NotebookReposCtrl',
              controllerAs: 'noterepo'
            })
            .when('/credential', {
              templateUrl: 'app/credential/credential.html',
              controller: 'CredentialCtrl'
            })
            .when('/configuration', {
              templateUrl: 'app/configuration/configuration.html',
              controller: 'ConfigurationCtrl'
            })
            .when('/search/:searchTerm', {
              templateUrl: 'app/search/result-list.html',
              controller: 'SearchResultCtrl'
            })
            .otherwise({
              redirectTo: '/'
            });

          ngToastProvider.configure({
            dismissButton: true,
            dismissOnClick: false,
            combineDuplications: true,
            timeout: 6000
          });
        });

  function auth() {
    var $http = angular.injector(['ng']).get('$http');
    var baseUrlSrv = angular.injector(['zeppelinWebApp']).get('baseUrlSrv');
    // withCredentials when running locally via grunt
    $http.defaults.withCredentials = true;
    jQuery.ajaxSetup({
      dataType: 'json',
      xhrFields: {
        withCredentials: true
      },
      crossDomain: true
    });
    return $http.get(baseUrlSrv.getRestApiBase() + '/security/ticket').then(function(response) {
      zeppelinWebApp.run(function($rootScope) {
        $rootScope.ticket = angular.fromJson(response.data).body;
      });
    }, function(errorResponse) {
      // Handle error case
    });
  }

  function bootstrapApplication() {
    zeppelinWebApp.run(function($rootScope, $location) {
      $rootScope.$on('$routeChangeStart', function(event, next, current) {
        if (!$rootScope.ticket && next.$$route && !next.$$route.publicAccess) {
          $location.path('/');
        }
      });
    });
    angular.bootstrap(document, ['zeppelinWebApp']);
  }

  angular.element(document).ready(function() {
    auth().then(bootstrapApplication);
  });
}());
