'use strict';

/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:NotebookCtrl
 * @description
 * # NotebookCtrl
 * Controller of the zeppelinWeb2App
 */
angular.module('zeppelinWeb2App').controller('NotebookCtrl', function($scope, $route, $routeParams, $location, $rootScope) {

  $scope.noteId = null;
  $scope.note = {};
  $scope.showEditor = false;
  
  /** Init the new controller */
  var initNotebook = function() {
    $scope.noteId = $routeParams.noteId;
    $rootScope.$emit('sendNewEvent', {op: 'GET_NOTE', data: {id: $scope.noteId}});
  };
  
  $scope.removeNote = function(noteId) {
    $rootScope.$emit('sendNewEvent', {op: 'DEL_NOTE', data: {id: noteId}});
    $location.path('/#');
  };
  
  $scope.sendNewName = function() {
    console.log('----------- %o', $scope.noteName);
    $scope.showEditor = false;
    if ($scope.noteName) {
      $rootScope.$emit('sendNewEvent', {op: 'NOTE_UPDATE', data: {id: $scope.noteId, name: $scope.noteName}});
    }
  };
  
  initNotebook();
  
  $rootScope.$on('setNoteContent', function(event, note) {
      $scope.note = note;
      if ($scope.note !== note.id) {
        $scope.noteId = note.id;
        $routeParams.noteId = note.id;
      }
  });
});
