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

angular.module('zeppelinWebApp').controller('HomeCtrl', function($scope, notebookListDataFactory, websocketMsgSrv, $rootScope) {
  
  var vm = this;
  vm.notes = notebookListDataFactory;
  vm.websocketMsgSrv = websocketMsgSrv;

  $scope.notebookHome = false;
  $scope.staticHome = false;
  
  var initHome = function() {
    websocketMsgSrv.getHomeNotebook();
  };

  initHome();

  $scope.$on('setNoteContent', function(event, note) {
    if (note) {
      $scope.note = note;

      // initialize look And Feel
      $rootScope.$broadcast('setLookAndFeel', 'home');

      // make it read only
      $scope.viewOnly = true;

      $scope.notebookHome = true;
    } else {
      $scope.staticHome = true;
    }
  });
});
