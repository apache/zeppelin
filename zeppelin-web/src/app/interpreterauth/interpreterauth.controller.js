/* jshint loopfunc: true */
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
angular.module('zeppelinWebApp').controller('InterpreterAuthCtrl',
  function($scope, $q, websocketMsgSrv, $rootScope, $http, baseUrlSrv, ngToast) {
  $scope._ = _;

  $scope.authList = [];

  $scope.intpList = [];
  $scope.userList = [];
  $scope.checklist = [];

  var isPaused = 0;

  $scope.isChanged = function(userIdx,intpIdx) {
    console.log('isChanged user: ', userIdx, ', intp: ', intpIdx);
    $scope.checklist[userIdx][intpIdx] ^= 1;
    console.log('after=', $scope.checklist);
  };

  var getInterpreterList = function() { return $q(function(success, fail) {
/*

    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/names').then(function
    (response) {
      $scope.intpList = angular.fromJson(response.data).body;
      for (var k in $scope.intpList) {
        console.log('intpList  -->', $scope.intpList[k]);
      }
    });
*/
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/names')
      .success(function(data, status, headers, config) {
        console.log('getInterpreterList %o', data);
        $scope.intpList = data.body;
        isPaused+=1;
        success();
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
        fail();
      });
  })};

  var getUserList = function() { return $q(function(success, fail) {
    $http.get(baseUrlSrv.getRestApiBase() + '/security/alluserlist')
      .success(function(data, status, headers, config) {
        console.log('getUserList %o', data);
        $scope.userList = data.body;
        isPaused+=1;
        success();
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
        fail();
      });
/*

    // get all user list.
    $http.get(baseUrlSrv.getRestApiBase() + '/security/alluserlist').then(function
    (response) {
      $scope.userList = angular.fromJson(response.data).body;
      for (var k in $scope.userList) {
        console.log('userlist  -->', $scope.userList[k]);
      }
    });
*/
  })};

  $scope.init = function() {
    console.log('init --------->');
    websocketMsgSrv.getInterpreterAuthList($rootScope.ticket.principal);
  };

  // request
  $scope.updateInterpreterAuth = function() {
    console.log('selected :', $scope.checklist);
//      console.log('updateInterpreterAuth ===>', $rootScope.ticket.principal);
//      websocketMsgSrv.updateInterpreterAuth($rootScope.ticket.principal, authList);
  };

  // request
  $scope.getInterpreterAuth = function() {
    console.log('getInterpreterAuth --------->', $scope.checklist);
    websocketMsgSrv.getInterpreterAuthList($rootScope.ticket.principal);
  }

  // receive
  $scope.$on('getInterpreterAuthList', function(event, data) {
    $scope.authList = data;

    _promise( true ).then(
      function (text) {
        console.log('success-', text);
        // make dimension.
        for (var i = 0; i < $scope.userList.length; i++) {
          $scope.checklist[i] = [];
          for (var j = 0; j < $scope.intpList.length; j++) {
            $scope.checklist[i][j] = isSelected($scope.userList[i], $scope.intpList[j]);
          }
        }
        console.log('----- end ---------', $scope.checklist);
      }
    );
  });

  var _promise = function(param) {
    return $q(function(resolve, reject) {
      getInterpreterList().then(function () {
        getUserList().then(
          function() {  // success
            resolve("success");
          },
          function() {  // success
            reject( Error("fail") );
          }
        );
      },
      function() {   // fail
        reject( Error("fail") );
      });
    });
  };

  var isSelected = function(userName, intpName) {
    var result = 0;
    $.each($scope.authList.authInfo, function(intp, userList) {
    if ( !_.isEmpty(_.where(userList, userName)) && intpName === intp) {
        console.log(userName + ' uses ' + intpName);
        result = 1;
      }
    });

    console.log('result ==>', result);
    return result;
  };
});
