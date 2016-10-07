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

  angular.module('zeppelinWebApp').controller('HomeCtrl', HomeCtrl);

  HomeCtrl.$inject = [
    '$scope',
    'notebookListDataFactory',
    'websocketMsgSrv',
    '$rootScope',
    'arrayOrderingSrv'
  ];

  function HomeCtrl($scope, notebookListDataFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv) {
    var vm = this;
    vm.notes = notebookListDataFactory;
    vm.websocketMsgSrv = websocketMsgSrv;
    vm.arrayOrderingSrv = arrayOrderingSrv;

    vm.notebookHome = false;
    if ($rootScope.ticket !== undefined) {
      vm.staticHome = false;
    } else {
      vm.staticHome = true;
    }

    $scope.isReloading = false;

    var initHome = function() {
      websocketMsgSrv.getHomeNotebook();
    };

    initHome();

    $scope.reloadNotebookList = function() {
      websocketMsgSrv.reloadAllNotesFromRepo();
      $scope.isReloadingNotes = true;
    };

    $scope.toggleFolderNode = function(node) {
      node.hidden = !node.hidden;
    };

    angular.element('#loginModal').on('hidden.bs.modal', function(e) {
      $rootScope.$broadcast('initLoginValues');
    });

    /*
    ** $scope.$on functions below
    */

    $scope.$on('setNoteMenu', function(event, notes) {
      $scope.isReloadingNotes = false;
    });

    $scope.$on('setNoteContent', function(event, note) {
      if (note) {
        vm.note = note;

        // initialize look And Feel
        $rootScope.$broadcast('setLookAndFeel', 'home');

        // make it read only
        vm.viewOnly = true;

        vm.notebookHome = true;
        vm.staticHome = false;
      } else {
        vm.staticHome = true;
        vm.notebookHome = false;
      }
    });
  }

})();
