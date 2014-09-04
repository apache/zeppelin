'use strict';

/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:NavCtrl
 * @description
 * # NavCtrl
 * Controller of the zeppelinWeb2App
 */
angular.module('zeppelinWeb2App').controller('NavCtrl', function($scope, $rootScope, $routeParams) {
  $scope.notes = [];

  $rootScope.$on('setNoteMenu', function(event, notes) {
    if (!event.defaultPrevented) {
      $scope.notes = notes;
      event.preventDefault();
    }
  });
  
  $scope.createNewNote = function() {
    $rootScope.$emit('sendNewEvent', {op: 'NEW_NOTE'});
  };
  
  $scope.isActive = function(noteId) {
    if ($routeParams.noteId === noteId) {
      return true;
    }
    return false;
  };
  
});
