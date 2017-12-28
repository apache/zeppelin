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

import 'headroom.js'
import 'headroom.js/dist/angular.headroom'

import 'scrollmonitor/scrollMonitor.js'
import 'angular-viewport-watch/angular-viewport-watch.js'

import 'angular-ui-grid/ui-grid.css'
import 'angular-ui-grid'

const requiredModules = [
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
  'ngclipboard',
  'angularViewportWatch',
  'infinite-scroll',
  'ui.grid',
  'ui.grid.exporter',
  'ui.grid.edit', 'ui.grid.rowEdit',
  'ui.grid.selection',
  'ui.grid.cellNav', 'ui.grid.pinning',
  'ui.grid.grouping',
  'ui.grid.emptyBaseLayer',
  'ui.grid.resizeColumns',
  'ui.grid.moveColumns',
  'ui.grid.pagination',
  'ui.grid.saveState',
]

// headroom should not be used for CI, since we have to execute some integration tests.
// otherwise, they will fail.
if (!process.env.BUILD_CI) { requiredModules.push('headroom') }

let zeppelinWebApp = angular.module('zeppelinWebApp', requiredModules)
  .filter('breakFilter', function () {
    return function (text) {
      // eslint-disable-next-line no-extra-boolean-cast
      if (!!text) {
        return text.replace(/\n/g, '<br />')
      }
    }
  })
  .config(function ($httpProvider, $routeProvider, ngToastProvider) {
    // withCredentials when running locally via grunt
    $httpProvider.defaults.withCredentials = true

    let visBundleLoad = {
      load: ['heliumService', function (heliumService) {
        return heliumService.load
      }]
    }

    $routeProvider
      .when('/', {
        templateUrl: 'app/home/home.html'
      })
      .when('/notebook/:noteId', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl',
        resolve: visBundleLoad
      })
      .when('/notebook/:noteId/paragraph?=:paragraphId', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl',
        resolve: visBundleLoad
      })
      .when('/notebook/:noteId/paragraph/:paragraphId?', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl',
        resolve: visBundleLoad
      })
      .when('/notebook/:noteId/revision/:revisionId', {
        templateUrl: 'app/notebook/notebook.html',
        controller: 'NotebookCtrl',
        resolve: visBundleLoad
      })
      .when('/jobmanager', {
        templateUrl: 'app/jobmanager/jobmanager.html',
        controller: 'JobManagerCtrl'
      })
      .when('/interpreter', {
        templateUrl: 'app/interpreter/interpreter.html',
        controller: 'InterpreterCtrl'
      })
      .when('/notebookRepos', {
        templateUrl: 'app/notebook-repository/notebook-repository.html',
        controller: 'NotebookRepositoryCtrl',
        controllerAs: 'noterepo'
      })
      .when('/credential', {
        templateUrl: 'app/credential/credential.html',
        controller: 'CredentialCtrl'
      })
      .when('/helium', {
        templateUrl: 'app/helium/helium.html',
        controller: 'HeliumCtrl'
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
      })

    ngToastProvider.configure({
      dismissButton: true,
      dismissOnClick: false,
      combineDuplications: true,
      timeout: 6000
    })
  })

  // handel logout on API failure
    .config(function ($httpProvider, $provide) {
      if (process.env.PROD) {
        $httpProvider.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest'
      }
      $provide.factory('httpInterceptor', function ($q, $rootScope) {
        return {
          'responseError': function (rejection) {
            if (rejection.status === 405) {
              let data = {}
              data.info = ''
              $rootScope.$broadcast('session_logout', data)
            }
            $rootScope.$broadcast('httpResponseError', rejection)
            return $q.reject(rejection)
          }
        }
      })
      $httpProvider.interceptors.push('httpInterceptor')
    })
  .constant('TRASH_FOLDER_ID', '~Trash')

function auth () {
  let $http = angular.injector(['ng']).get('$http')
  let baseUrlSrv = angular.injector(['zeppelinWebApp']).get('baseUrlSrv')
  // withCredentials when running locally via grunt
  $http.defaults.withCredentials = true
  jQuery.ajaxSetup({
    dataType: 'json',
    xhrFields: {
      withCredentials: true
    },
    crossDomain: true
  })
  let config = (process.env.PROD) ? {headers: { 'X-Requested-With': 'XMLHttpRequest' }} : {}
  return $http.get(baseUrlSrv.getRestApiBase() + '/security/ticket', config).then(function (response) {
    zeppelinWebApp.run(function ($rootScope) {
      let res = angular.fromJson(response.data).body
      if (res['redirectURL']) {
        window.location.href = res['redirectURL'] + window.location.href
      } else {
        $rootScope.ticket = res
        $rootScope.ticket.screenUsername = $rootScope.ticket.principal
        if ($rootScope.ticket.principal.indexOf('#Pac4j') === 0) {
          let re = ', name=(.*?),'
          $rootScope.ticket.screenUsername = $rootScope.ticket.principal.match(re)[1]
        }
      }
    })
  }, function (errorResponse) {
    // Handle error case
    let redirect = errorResponse.headers('Location')
    if (errorResponse.status === 401 && redirect !== undefined) {
      // Handle page redirect
      window.location.href = redirect
    }
  })
}

function bootstrapApplication () {
  zeppelinWebApp.run(function ($rootScope, $location) {
    $rootScope.$on('$routeChangeStart', function (event, next, current) {
      $rootScope.pageTitle = 'Zeppelin'
      if (!$rootScope.ticket && next.$$route && !next.$$route.publicAccess) {
        $location.path('/')
      }
    })
  })
  angular.bootstrap(document, ['zeppelinWebApp'])
}

angular.element(document).ready(function () {
  auth().then(bootstrapApplication)
})
