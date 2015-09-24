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

angular.module('zeppelinWebApp').controller('HomeCtrl', function($scope, notebookListDataFactory, websocketMsgSrv, $rootScope, arrayOrderingSrv) {
  
  var vm = this;
  vm.notes = notebookListDataFactory;
  vm.websocketMsgSrv = websocketMsgSrv;
  vm.arrayOrderingSrv = arrayOrderingSrv;

  vm.notebookHome = false;
  vm.staticHome = false;
  
  var initHome = function() {
    websocketMsgSrv.getHomeNotebook();
  };

  initHome();

  $scope.time2TimeAgo = function(ts) {
    var d = new Date();
    var nowTs = Math.floor(d.getTime()/1000);
    var seconds = nowTs-(ts/1000);

    if (seconds > 2*24*3600*31 * 12) {
      return Math.floor(seconds/2/24/3600/31/12) + ' few year ago';
    }

    if (seconds > 2*24*3600 * 31) {
      return Math.floor(seconds/2/24/3600/31) + ' few month ago';
    }

    if (seconds > 2*24*3600) {
      return Math.floor(seconds/2/24/3600) + ' few days ago';
    }

    // a day
    if (seconds > 24*3600) {
      return 'yesterday';
    }

    // hours or min 
    if (seconds > 3600) {
      return Math.floor(seconds/3600) + ' few hours ago';
    }

    if (seconds > 60) {
      return Math.floor(seconds/60) + ' minutes ago';
    }

	console.log('time ' + seconds);
    return 'New';
  };

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
});
