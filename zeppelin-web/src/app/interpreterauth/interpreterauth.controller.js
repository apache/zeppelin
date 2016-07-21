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

//  $scope.intpList = [];
//  $scope.userList = [];

//  $scope.user ={
//    roles: [1]
//  };
  $scope.intpList = ["spark","livy","jdbc","flink","geode"];
  $scope.userList = ["user1","user2","user3","user4","user5","user6"];

  $scope.mydata = [
      {"user1" : [0,1,0,0,1]},
      {"user2" : [1,1,0,0,1]},
      {"user3" : [0,1,0,1,1]},
      {"user4" : [0,1,1,0,0]},
      {"user5" : [1,1,1,1,1]}
      ];

  $scope.isChanged = function(userIdx,intpIdx) {
    console.log('isChanged user: ', userIdx, ', intp: ', intpIdx);
  };

/*
  $scope.isChecked = function(userIdx,intpIdx) {

    console.log('checked user: ', userIdx, ', intp: ', intpIdx);
*/
/*
    var items = $scope.my.profile.items;
    for (var i=0; i < items.length; i++) {
      if (product.id == items[i].id)
        return true;
    }

    return false;
*//*

  };
*/

  $scope.init = function() {
    console.log('init --------->');
    websocketMsgSrv.getInterpreterAuthList();
  };

  $scope.updateInterpreterAuth = function() {
    // get all user list.
    $http.get(baseUrlSrv.getRestApiBase() + '/security/alluserlist').then(function
    (response) {
      $scope.userList = angular.fromJson(response.data).body;
      for (var k in $scope.userList) {
        console.log('  -->', $scope.userList[k]);
      }

      var authList = { "authInfo": [
                         {"intp1": [ "user1", "user2" ]} ,
                         {"intp2": [ "user1" ]}
                     ]};


      //var authList = { "authInfo": { "spark": [ "user1", "user2" ] , "livy": [ "user1" ] } };
/*
      var authList = {
                      "intp1": [ "user1", "user2" ] ,
                      "intp2": [ "user1" ]
                  };
*/

      $scope.intpList = _.keys(_.pick(authList.authInfo, function(value) {
        return value;
      }));
      console.log('name----->', $scope.intpList);






//      console.log('updateInterpreterAuth ===>', $rootScope.ticket.principal);
//      websocketMsgSrv.updateInterpreterAuth($rootScope.ticket.principal, authList);

      console.log('----- end ---------');
    });
  };

  $scope.getInterpreterAuth = function() {
    console.log('getInterpreterAuth --------->', $scope.user);
  }

  // receive
  $scope.$on('getInterpreterAuth', function(event, data) {
    console.log('getInterpreterAuth --------->', data);
    websocketMsgSrv.getInterpreterAuthList();
  });

});
