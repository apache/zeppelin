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

angular.module('zeppelinWebApp').controller('PropertyCtrl', function($scope, $rootScope, $http, baseUrlSrv, ngToast) {
  $scope._ = _;

  $scope.PropertyInfo = [];
  $scope.showAddNewPropertyInfo = false;

  var getPropertyInfo = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/property').
    success(function(data, status, headers, config) {
      $scope.PropertyInfo  = _.map(data.body, function(value, prop) {
        return {name: prop, value: value};
      });
      console.log('Success %o %o', status, $scope.PropertyInfo);
    }).
    error(function(data, status, headers, config) {
      if (status === 401) {
        ngToast.danger({
          content: 'You don\'t have permission on this page',
          verticalPosition: 'bottom',
          timeout: '3000'
        });
        setTimeout(function() {
          window.location.replace('/');
        }, 3000);
      }
      console.log('Error %o %o', status, data.message);
    });
  };

  $scope.addNewPropertyInfo = function() {
    if ($scope.name && _.isEmpty($scope.name.trim()) &&
      $scope.value && _.isEmpty($scope.value.trim())) {
      ngToast.danger({
        content: 'name \\ value can not be empty.',
        verticalPosition: 'bottom',
        timeout: '3000'
      });
      return;
    }

    var newProperty  = {
      'name': $scope.name,
      'value': $scope.value
    };

    $http.put(baseUrlSrv.getRestApiBase() + '/property', newProperty).
    success(function(data, status, headers, config) {
      ngToast.success({
        content: 'Successfully saved properties.',
        verticalPosition: 'bottom',
        timeout: '3000'
      });
      $scope.PropertyInfo.push(newProperty);
      resetPropertyInfo();
      $scope.showAddNewPropertyInfo = false;
      console.log('Success %o %o', status, data.message);
    }).
    error(function(data, status, headers, config) {
      ngToast.danger({
        content: 'Error saving properties',
        verticalPosition: 'bottom',
        timeout: '3000'
      });
      console.log('Error %o %o', status, data.message);
    });
  };

  $scope.cancelPropertyInfo = function() {
    $scope.showAddNewPropertyInfo = false;
    resetPropertyInfo();
  };

  var resetPropertyInfo = function() {
    $scope.name = '';
    $scope.value = '';
  };

  $scope.copyOriginPropertysInfo = function() {
    ngToast.info({
      content: 'Since name is a unique key, you can edit only value',
      verticalPosition: 'bottom',
      timeout: '3000'
    });
  };

  $scope.updatePropertyInfo = function(form, data, name) {
    var request = {
      name: name,
      value: data.value
    };

    $http.put(baseUrlSrv.getRestApiBase() + '/property/', request).
    success(function(data, status, headers, config) {
      var index = _.findIndex($scope.PropertyInfo, {'name': name});
      $scope.PropertyInfo[index] = request;
      return true;
    }).
    error(function(data, status, headers, config) {
      console.log('Error %o %o', status, data.message);
      ngToast.danger({
        content: 'We couldn\'t save the Property',
        verticalPosition: 'bottom',
        timeout: '3000'
      });
      form.$show();
    });
    return false;
  };

  $scope.removePropertyInfo = function(name) {
    BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to delete this Property information?',
      callback: function(result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/property/' + name).
          success(function(data, status, headers, config) {
            var index = _.findIndex($scope.PropertyInfo, {'name': name});
            $scope.PropertyInfo.splice(index, 1);
            console.log('Success %o %o', status, data.message);
          }).
          error(function(data, status, headers, config) {
            console.log('Error %o %o', status, data.message);
          });
        }
      }
    });
  };

  var init = function() {
    getPropertyInfo();
  };

  init();
});
