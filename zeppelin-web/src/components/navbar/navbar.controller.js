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
.controller('NavCtrl', function($scope, $rootScope, $http, $routeParams,
    $location, notebookListDataFactory, baseUrlSrv, websocketMsgSrv, arrayOrderingSrv, searchService) {

  var vm = this;
  vm.arrayOrderingSrv = arrayOrderingSrv;
  vm.connected = websocketMsgSrv.isConnected();
  vm.notes = notebookListDataFactory;
  vm.searchForm = searchService;
  vm.websocketMsgSrv = websocketMsgSrv;

  $scope.query = {q: ''};
  /** Current list of notes (ids) */

  angular.element('#notebook-list').perfectScrollbar({suppressScrollX: true});

  angular.element(document).click(function() {
    $scope.query.q = '';
  });

  function getZeppelinVersion() {
    $http.get(baseUrlSrv.getRestApiBase() + '/version').success(
      function(data, status, headers, config) {
        $rootScope.zeppelinVersion = data.body;
      }).error(
      function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
  }

  function isActive(noteId) {
    return ($routeParams.noteId === noteId);
  }

  function loadNotes() {
    websocketMsgSrv.getNotebookList();
  }

  function logout() {
    var logoutURL = baseUrlSrv.getRestApiBase() + '/login/logout';

    //for firefox and safari
    logoutURL = logoutURL.replace('//', '//false:false@');
    $http.post(logoutURL).error(function() {
      //force authcBasic (if configured) to logout
      $http.post(logoutURL).error(function() {
        $rootScope.userName = '';
        $rootScope.ticket.principal = '';
        $rootScope.ticket.ticket = '';
        $rootScope.ticket.roles = '';
        BootstrapDialog.show({
          message: 'Logout Success'
        });
        setTimeout(function() {
          window.location.replace('/');
        }, 1000);
      });
    });
  }

  function search(searchTerm) {
    $location.path('/search/' + searchTerm);
  }

  function showLoginWindow() {
    setTimeout(function() {
      angular.element('#userName').focus();
    }, 500);
  }

  $rootScope.noteName = function(note) {
    if (!_.isEmpty(note)) {
      return arrayOrderingSrv.getNoteName(note);
    }
  };

  vm.loadNotes = loadNotes;
  vm.isActive = isActive;
  vm.logout = logout;
  vm.search = search;
  vm.showLoginWindow = showLoginWindow;

  getZeppelinVersion();
  vm.loadNotes();

  /*
  ** $scope.$on functions below
  */

  $scope.$on('setNoteMenu', function(event, notes) {
    notebookListDataFactory.setNotes(notes);
  });

  $scope.$on('setConnectedStatus', function(event, param) {
    vm.connected = param;
  });

  $scope.$on('loginSuccess', function(event, param) {
    vm.loadNotes();
  });

});
