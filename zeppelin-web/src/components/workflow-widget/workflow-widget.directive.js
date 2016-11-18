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

angular.module('zeppelinWebApp').controller('workflowWidgetCtrl', function($scope, notebookListDataFactory,
                                                                           $rootScope, $routeParams, websocketMsgSrv) {
  console.log('data ', $scope.workflowJobResultData);

  $scope.workflowJobLists = [];
  $scope.workflowJobResultData = [];

  $scope.$watchCollection('workflowJobInputData', function() {
    var NOTE_NOT_FOUNT = -1;
    $scope.workflowJobLists = [];
    $scope.workflowJobResultData = [];
    var noteLists = notebookListDataFactory.root.children;
    $scope.workflowJobInputData.map(function(item) {
      var workflowJob = item.value.split(':');
      if (workflowJob[0] === undefined) {
        return;
      }
      if (workflowJob[1] === undefined) {
        workflowJob[1] = '*';
      }

      var noteIndex = _.findIndex(noteLists, {'id': workflowJob[0]});
      if (noteIndex === NOTE_NOT_FOUNT) {
        return;
      }

      var paragraphNameValue = workflowJob[1] === '*' ? 'ALL Paragraph' : workflowJob[1];
      $scope.workflowJobLists.push(
        {
          noteName: noteLists[noteIndex].name,
          paragraphName: paragraphNameValue,
          notebookId: workflowJob[0],
          paragraphId: workflowJob[1]
        }
      );
      $scope.workflowJobResultData.push({notebookId: workflowJob[0], paragraphId: workflowJob[1]});
    });
  });

  $scope.removeJob = function(jobIndex) {
    $scope.workflowJobInputData.splice(jobIndex, 1);
  };

})
  .directive('workflowWidget', function() {
  return {
    restrict: 'E',
    scope: {
      'workflowJobInputData': '=',
      'workflowJobResultData': '='
    },
    controller: 'workflowWidgetCtrl',
    templateUrl: 'components/workflow-widget/workflow-widget.html',
    link: function(scope, elem, attrs) {
    }
  };
});
