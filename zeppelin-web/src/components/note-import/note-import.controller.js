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

import './note-import.css';

angular.module('zeppelinWebApp').controller('NoteImportCtrl', NoteImportCtrl);

function NoteImportCtrl($scope, $timeout, websocketMsgSrv, $http, baseUrlSrv) {
  'ngInject';

  let vm = this;
  $scope.note = {};
  $scope.note.step1 = true;
  $scope.note.step2 = false;
  $scope.maxLimit = '';

  vm.resetFlags = function() {
    $scope.note = {};
    $scope.note.step1 = true;
    $scope.note.step2 = false;
    angular.element('#noteImportFile').val('');
  };

  $scope.uploadFile = function() {
    angular.element('#noteImportFile').click();
  };

  $scope.resetFile = function(element) {
    element.value = '';
  };

  $scope.importFile = function(element) {
    $scope.note.errorText = '';
    $scope.note.importFile = element.files[0];
    let file = $scope.note.importFile;
    let reader = new FileReader();

    $http.get(baseUrlSrv.getRestApiBase() + '/wsMaxMessageSize')
      .then(function(response) {
        const limit = response.data.body;

        if (file.size > limit) {
          $scope.note.errorText = 'File size limit Exceeded!';
          $scope.$apply();
          return;
        }

        reader.onloadend = function() {
          vm.processImportJson(reader.result);
        };

        if (file) {
          reader.readAsText(file);
        }
      })
      .catch(function(err) {
        console.error('Error while fetching max message size', err);
        $scope.note.errorText = 'Unable to get upload limit.';
        $scope.$apply();
      });
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
      jQuery.ajax({
        url: $scope.note.importUrl,
        type: 'GET',
        dataType: 'json',
        jsonp: false,
        xhrFields: {
          withCredentials: false,
        },
        error: function(xhr, ajaxOptions, thrownError) {
          $scope.note.errorText = 'Unable to Fetch URL';
          $scope.$apply();
        }}).done(function(data) {
          vm.processImportJson(data);
        });
    } else {
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
      // zeppelin notebook format
      if (!$scope.note.noteImportName) {
        $scope.note.noteImportName = result.name;
      } else {
        result.name = $scope.note.noteImportName;
      }
      websocketMsgSrv.importNote(result);
      // angular.element('#noteImportModal').modal('hide');
    } else if (result.cells && result.cells.length > 0) {
      // nbviewer notebook format
      if (!$scope.note.noteImportName) {
        $scope.note.noteImportName = result.metadata.kernelspec.display_name;
      }
      result.name = $scope.note.noteImportName;
      websocketMsgSrv.importNote(result);
      // angular.element('#noteImportModal').modal('hide');
    } else {
      $scope.note.errorText = 'Invalid JSON';
    }
    $scope.$apply();
  };

  /*
   ** $scope.$on functions below
   */

  $scope.$on('noteImported', function(event, note) {
    vm.resetFlags();
    angular.element('#noteImportModal').modal('hide');
  });
}
