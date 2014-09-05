/* Copyright 2014 NFLabs
 *
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

/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:NotebookCtrl
 * @description
 * # NotebookCtrl
 * Controller of notes, manage the note (update)
 * 
 * @author anthonycorbacho
 */
angular.module('zeppelinWeb2App').controller('NotebookCtrl', function($scope, $route, $routeParams, $location, $rootScope) {

  $scope.note = null;
  $scope.showEditor = false;
  
  /** Init the new controller */
  var initNotebook = function() {
    $rootScope.$emit('sendNewEvent', {op: 'GET_NOTE', data: {id: $routeParams.noteId}});
  };
  
  initNotebook();
  
  /** Remove the note and go back tot he main page */
  /** TODO(anthony): In the nearly future, go back to the main page and telle to the dude that the note have been remove */
  $scope.removeNote = function(noteId) {
    $rootScope.$emit('sendNewEvent', {op: 'DEL_NOTE', data: {id: $scope.note.id}});
    $location.path('/#');
  };
  
  /** Update the note name */
  $scope.sendNewName = function() {
    $scope.showEditor = false;
    if ($scope.noteName) {
      $rootScope.$emit('sendNewEvent', {op: 'NOTE_UPDATE', data: {id: $scope.note.id, name: $scope.noteName}});
    }
  };
  
  /** update the current note */
  $rootScope.$on('setNoteContent', function(event, note) {
    if ($scope.note === null) {
      $scope.note = note;
    } else {
      updateNote(note);
    }
  });
  
  var updateNote = function(note) {
    /** update Note name */
    if (note.name !== $scope.note.name) {
      console.log('change note name: %o to %o', $scope.note.name, note.name);
      $scope.note.name = note.name;
    }
    /** add new paragraphs */
    note.paragraphs.forEach(function(newEntry) {
     var found = false;
      $scope.note.paragraphs.forEach(function(currentEntry) {
        if (currentEntry.id === newEntry.id) {
          found = true;
          $rootScope.$emit('updateParagraph', {paragraph: newEntry});
        }
      });
      /** not found means addnewpara */
      if(!found) {
        $scope.note.paragraphs.push(newEntry);
      }
    });
    
    /** remove paragraphs */
    for (var entry in $scope.note.paragraphs) {
     var found = false;
      note.paragraphs.forEach(function(currentEntry) {
        if (currentEntry.id === $scope.note.paragraphs[entry].id) {
          found = true;
        }
      });
      /** not found means bye */
      if(!found) {
        $scope.note.paragraphs.splice(entry, 1)
      }
    };
    
  };
  
});
