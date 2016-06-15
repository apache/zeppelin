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

angular.module('zeppelinWebApp')
.filter('notebookFilter', function() {
  return function (notebooks, searchText)
  {
    if (!searchText) {
      return notebooks;
    }

    var filteringNote = function(notebooks, filteredNotes) {
      _.each(notebooks, function(notebook) {

        if (notebook.name.toLowerCase().indexOf(searchText) !== -1) {
          filteredNotes.push(notebook);
          return notebook;
        }

        if (notebook.children) { 
          filteringNote(notebook.children, filteredNotes);
        }
      });
    };

    return _.filter(notebooks, function(notebook) {
      if (notebook.children) {
        var filteredNotes = [];
        filteringNote(notebook.children, filteredNotes);

        if (filteredNotes.length > 0) {
          return filteredNotes;
        }
      }

      if (notebook.name.toLowerCase().indexOf(searchText) !== -1) {
        return notebook;
      }
    });
  };
})
.controller('NavCtrl', function($scope, $rootScope, $http, $routeParams,
    $location, notebookListDataFactory, baseUrlSrv, websocketMsgSrv, arrayOrderingSrv) {
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

  $scope.checkUsername = function () {
    if ($rootScope.ticket) {
      if ($rootScope.ticket.principal.length <= MAX_USERNAME_LENGTH) {
        $rootScope.truncatedUsername = $rootScope.ticket.principal;
      }
      else {
        $rootScope.truncatedUsername = $rootScope.ticket.principal.substr(0, MAX_USERNAME_LENGTH) + '..';
      }
    }
    if (_.isEmpty($rootScope.truncatedUsername)) {
      $rootScope.truncatedUsername = 'Connected';
    }
  };

  $scope.$on('loginSuccess', function(event, param) {
    $scope.checkUsername();
    loadNotes();
  });

  $scope.logout = function() {
    $http.post(baseUrlSrv.getRestApiBase()+'/login/logout')
      .success(function(data, status, headers, config) {
        $rootScope.userName = '';
        $rootScope.ticket.principal = '';
        $rootScope.ticket.ticket = '';
        $rootScope.ticket.roles = '';
        BootstrapDialog.show({
           message: 'Logout Success'
        });
        setTimeout(function() {
          window.location = '#';
          window.location.reload();
        }, 1000);
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.search = function(searchTerm) {
    $location.url(/search/ + searchTerm);
  };

  function loadNotes() {
    websocketMsgSrv.getNotebookList();
  }

  function isActive(noteId) {
    return ($routeParams.noteId === noteId);
  }

  $rootScope.noteName = function(note) {
    if (!_.isEmpty(note)) {
      return arrayOrderingSrv.getNoteName(note);
    }
  };

  function getZeppelinVersion() {
    console.log('version');
    $http.get(baseUrlSrv.getRestApiBase() + '/version').success(
      function(data, status, headers, config) {
        $rootScope.zeppelinVersion = data.body;
      }).error(
      function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  }

  vm.loadNotes = loadNotes;
  vm.isActive = isActive;

  getZeppelinVersion();
  vm.loadNotes();
  $scope.checkUsername();

});
