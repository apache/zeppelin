/*
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
(function() {

  angular.module('zeppelinWebApp').controller('ResultCtrl', ResultCtrl);

  ResultCtrl.$inject = [
    '$scope',
    '$rootScope',
    '$route',
    '$window',
    '$routeParams',
    '$location',
    '$timeout',
    '$compile',
    '$http',
    '$q',
    'websocketMsgSrv',
    'baseUrlSrv',
    'ngToast',
    'saveAsService'
  ];

  function ResultCtrl($scope, $rootScope, $route, $window, $routeParams, $location,
                         $timeout, $compile, $http, $q, websocketMsgSrv,
                         baseUrlSrv, ngToast, saveAsService) {

    $scope.parentNote = null;
    $scope.paragraph = null;
    $scope.originalText = '';
    $scope.editor = null;

    $scope.init = function(results, configs) {
      console.log('result controller init %o %o', results, configs);
    };
  };
})();
