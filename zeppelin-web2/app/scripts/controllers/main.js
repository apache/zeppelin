'use strict';
/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the zeppelinWeb2App
 */
angular.module('zeppelinWeb2App')
       .controller('MainCtrl', function($scope) {
  $scope.awesomeThings = [
    'HTML5 Boilerplate',
    'AngularJS',
    'Karma'
  ];

  // Controller init
  $scope.init = function() {
  };

  $scope.hiddenMenu = false;
  $scope.hideMenu = function() {
    if ($scope.hiddenMenu === true) {
      $scope.hiddenMenu = false;
    } else {
      $scope.hiddenMenu = true;
    }
  };
});
