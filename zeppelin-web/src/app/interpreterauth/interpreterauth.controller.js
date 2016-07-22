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
  $scope.checkedlist = [];

  var getInterpreterList = function() {
    return $q(function(success, fail) {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/names')
      .success(function(data, status, headers, config) {
        console.log('getInterpreterList %o', data);
        $scope.intpList = data.body;
        success();
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
        fail();
      });
    });
  };

  var getUserList = function() {
    return $q(function(success, fail) {
      $http.get(baseUrlSrv.getRestApiBase() + '/security/alluserlist')
      .success(function(data, status, headers, config) {
        console.log('getUserList %o', data);
        $scope.userList = data.body;
        success();
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
        fail();
      });
    });
  };

  $scope.init = function() {
    websocketMsgSrv.getInterpreterAuthList($rootScope.ticket.principal);
  };

  // request
  $scope.updateInterpreterAuth = function() {
    var intpAuthObj = new Object();
    for (var i = 0; i < $scope.checkedlist.length; i++) {

      for (var j = 0; j < $scope.checkedlist[i].length; j++) {
        if ($scope.checkedlist[i][j] === true) {
          console.log($scope.intpList[i], '(', i, ') use ', $scope.userList[j], '(', j, ')');

          if (intpAuthObj[$scope.intpList[i]] === undefined) {
            intpAuthObj[$scope.intpList[i]] = new Array();
          }
          intpAuthObj[$scope.intpList[i]].push($scope.userList[j]);
        }
      }
    }
    websocketMsgSrv.updateInterpreterAuth($rootScope.ticket.principal, intpAuthObj);
  };

  // request
  $scope.getInterpreterAuth = function() {
    websocketMsgSrv.getInterpreterAuthList($rootScope.ticket.principal);
  };

  // receive
  $scope.$on('getInterpreterAuthList', function(event, data) {
    $scope.authList = data;

    _promise(true).then(
      function() {
        // make dimension.
        for (var i = 0; i < $scope.intpList.length; i++) {
          $scope.checkedlist[i] = [];
          for (var j = 0; j < $scope.userList.length; j++) {
            $scope.checkedlist[i][j] = isSelected($scope.intpList[i], $scope.userList[j]);
          }
        }
      }
    );
  });

  var _promise = function(param) {
    return $q(function(resolve, reject) {
      getInterpreterList().then(function() {
        getUserList().then(
          function() {
            resolve('success');
          },
          function() {
            reject(Error('fail'));
          }
        );
      },
      function() {
        reject(Error('fail'));
      });
    });
  };

  var isSelected = function(intpName, userName) {
    var result = false;
    /*
    $.each($scope.authList.authInfo, function(intp, userList) {
      if (!_.isEmpty(_.where(userList, userName)) && intpName === intp) {
        console.log(userName + ' uses ' + intpName);
        result = true;
      }
    });
    */
    for (var intp in $scope.authList.authInfo) {
      var userList = $scope.authList.authInfo[intp];
      if (!_.isEmpty(_.where(userList, userName)) && intpName === intp) {
        result = true;
      }
    }
    return result;
  };
});
