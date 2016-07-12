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
    $scope.workflowJobLists = [];
    $scope.workflowJobResultData = [];
    $scope.workflowJobInputData.map(function(item) {
      var workflowJob = item.value.split(':');
      if (workflowJob[0] === undefined || workflowJob[1] === undefined) {
        return;
      }
      $scope.workflowJobLists.push({notebookId: workflowJob[0], paragraphId: workflowJob[1]});
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
