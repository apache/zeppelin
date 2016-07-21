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
  function($scope, websocketMsgSrv, $rootScope, $http, baseUrlSrv, ngToast) {
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
/*
    for(var attr in data.authInfo) {
      console.log('-->', data.authInfo[attr]);
      var d = data.authInfo[attr];
      $.each(d, function(k, v) {
          //display the key and value pair
          console.log(k + ' is ' + v);
      });
    }
*/

/*
    var authList = angular.toJson(data);
    console.log('-->', authList);
*/

/*

    var authList = { "authInfo": [
                       {"intp1": [ "user1", "user2" ]} ,
                       {"intp2": [ "user1" ]}
                   ]};
    for(var idx in data.authInfo) {
      var authInfo = data.authInfo[idx];
      $.each(authInfo, function(intpName, userList) {
          //display the key and value pair
          console.log(intpName + ' is ' + userList);
      });
    }
*/

/*
    var authList = { "authInfo": { "spark": [ "user1", "user2" ] , "livy": [ "user1" ] } };
    $scope.intpList = _.keys(_.pick(authList.authInfo, function(value) {
      return value;
    }));
*/
    $scope.authList = data;
/*

    $scope.intpList = _.keys(_.pick(data.authInfo, function(value) {
      return value;
    }));
    console.log('name----->', $scope.intpList, ', len=', $scope.intpList.length);
*/

    getInterpreterList();
    getUserList();

    function waitForIt() {
      console.log('isPaused = ', isPaused);
      if (isPaused >= 2) {
          setTimeout(function(){waitForIt()},100);
      } else {
          // go do that thing
      };
    }


/*

    // get all interpreter list.
    getInterpreterList();
    // get all user list.
    getUserList();

    ttt(function () { // <-- this will run once all the above animations are finished

    });
*/


    // make dimension.
    for (var i = 0; i < $scope.userList.length; i++) {
      $scope.checklist[i] = [];
      for (var j = 0; j < $scope.intpList.length; j++) {
        $scope.checklist[i][j] = isSelected($scope.userList[i], $scope.intpList[j]);
      }
    }
    console.log('----- end ---------', $scope.checklist);
  });

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

  var ttt = function(callback) {
    console.log('ttttttttttttt');
  };

  var getInterpreterList = function() {
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
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
      });
  };

  var getUserList = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/security/alluserlist')
      .success(function(data, status, headers, config) {
        console.log('getUserList %o', data);
        $scope.userList = data.body;
        isPaused+=1;
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
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
  };
});
