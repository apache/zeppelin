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

angular.module('zeppelinWebApp').controller('NoteImportCtrl', function($scope, $timeout, websocketMsgSrv) {
  var vm = this;
  $scope.note = {};
  $scope.note.step1 = true;
  $scope.note.step2 = false;

  vm.resetFlags = function() {
    $scope.note = {};
    $scope.note.step1 = true;
    $scope.note.step2 = false;
    angular.element('#noteImportFile').val('');
  };

  $scope.uploadFile = function() {
    angular.element('#noteImportFile').click();
  };

  $scope.importFile = function(element) {
    $scope.note.errorText = '';
    $scope.note.importFile = element.files[0];
    var file = $scope.note.importFile;
    var reader = new FileReader();

    reader.onloadend = function() {
      vm.processImportJson(reader.result);
    };

    if (file) {
      reader.readAsText(file);
    }
  };

  $scope.uploadURL = function() {
    $scope.note.errorText = '';
    $scope.note.step1 = false;
    $timeout(function() {
      $scope.note.step2 = true;
    }, 400);
  };

  vm.importBack = function() {
    $scope.note.errorText = '';
    $timeout(function() {
      $scope.note.step1 = true;
    }, 400);
    $scope.note.step2 = false;
  };

  vm.importNote = function() {
    $scope.note.errorText = '';
    if ($scope.note.importUrl) {
      jQuery.getJSON($scope.note.importUrl, function(result) {
        vm.processImportJson(result);
      }).fail(function() {
        $scope.note.errorText = 'Unable to Fetch URL';
        $scope.$apply();
      });
    }
    else {
      $scope.note.errorText = 'Enter URL';
      $scope.$apply();
    }
  };

  vm.processImportJson = function(result) {
    if (typeof result !== 'object') {
      try {
        result = JSON.parse(result);
      } catch (e) {
        $scope.note.errorText = 'JSON parse exception';
        $scope.$apply();
        return;
      }

    }
    if (result.paragraphs && result.paragraphs.length > 0) {
      if (!$scope.note.noteImportName) {
        $scope.note.noteImportName = result.name;
      } else {
        result.name = $scope.note.noteImportName;
      }
      websocketMsgSrv.importNotebook(result);
      //angular.element('#noteImportModal').modal('hide');
    } else {
      $scope.note.errorText = 'Invalid JSON';
    }
    $scope.$apply();
  };

  $scope.$on('setNoteMenu', function(event, notes) {
    vm.resetFlags();
    angular.element('#noteImportModal').modal('hide');
  });
});
