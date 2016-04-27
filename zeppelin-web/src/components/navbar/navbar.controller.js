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

angular.module('zeppelinWebApp').controller('NavCtrl', function($scope, $rootScope, $routeParams,
    $location, notebookListDataFactory, websocketMsgSrv, arrayOrderingSrv) {
  /** Current list of notes (ids) */

  $scope.showLoginWindow = function() {
    setTimeout(function() {
      angular.element('#userName').focus();
    }, 500);
  };

  var vm = this;
  vm.notes = notebookListDataFactory;
  vm.connected = websocketMsgSrv.isConnected();
  vm.websocketMsgSrv = websocketMsgSrv;
  vm.arrayOrderingSrv = arrayOrderingSrv;
  if ($rootScope.ticket) {
    $rootScope.fullUsername = $rootScope.ticket.principal;
    $rootScope.truncatedUsername = $rootScope.ticket.principal;
  }

  var MAX_USERNAME_LENGTH=16;

  angular.element('#notebook-list').perfectScrollbar({suppressScrollX: true});

  $scope.$on('setNoteMenu', function(event, notes) {
    notebookListDataFactory.setNotes(notes);
  });

  $scope.$on('setConnectedStatus', function(event, param) {
    vm.connected = param;
  });

  $rootScope.$on('$locationChangeSuccess', function () {
    var path = $location.path();
    // hacky solution to clear search bar
    // TODO(felizbear): figure out how to make ng-click work in navbar
    if (path === '/') {
      $scope.searchTerm = '';
    }
  });

  $scope.checkUsername = function () {
    if ($rootScope.ticket) {
      if ($rootScope.ticket.principal.length <= MAX_USERNAME_LENGTH) {
        $rootScope.truncatedUsername = $rootScope.ticket.principal;
      }
      else {
        $rootScope.truncatedUsername = $rootScope.ticket.principal.substr(0, MAX_USERNAME_LENGTH) + '..';
      }
    }
  };

  $scope.$on('loginSuccess', function(event, param) {
    $scope.checkUsername();
    loadNotes();
  });

  $scope.search = function() {
    $location.url(/search/ + $scope.searchTerm);
  };

  function loadNotes() {
    websocketMsgSrv.getNotebookList();
  }

  function isActive(noteId) {
    return ($routeParams.noteId === noteId);
  }

  vm.loadNotes = loadNotes;
  vm.isActive = isActive;

  vm.loadNotes();
  $scope.checkUsername();

});
