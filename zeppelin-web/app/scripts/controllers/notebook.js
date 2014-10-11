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
 * @name zeppelinWebApp.controller:NotebookCtrl
 * @description
 * # NotebookCtrl
 * Controller of notes, manage the note (update)
 *
 * @author anthonycorbacho
 */
angular.module('zeppelinWebApp').controller('NotebookCtrl', function($scope, $route, $routeParams, $location, $rootScope) {

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
    var result = confirm('Do you want to delete this notebook?');
    if (result) {
      $rootScope.$emit('sendNewEvent', {op: 'DEL_NOTE', data: {id: $scope.note.id}});
      $location.path('/#');
    }
  };

  $scope.runNote = function(noteId) {
    var result = confirm('Run all paragraphs?');
    if (result) {
      $rootScope.$emit('runParagraph');
    }
  };

  $scope.showAllEditor = function() {
    $rootScope.$emit('openEditor');
  };

  $scope.hideAllEditor = function() {
    $rootScope.$emit('closeEditor');
  };

  $scope.isNoteRunning = function() {
    var running = false;
    if(!$scope.note) return false;
    for (var i=0; i<$scope.note.paragraphs.length; i++) {
      if ( $scope.note.paragraphs[i].status === "PENDING" || $scope.note.paragraphs[i].status === "RUNNING") {
        running = true;
        break;
      }
    }
    return running;
  };

  /** Update the note name */
  $scope.sendNewName = function() {
    $scope.showEditor = false;
    if ($scope.note.name) {
      $rootScope.$emit('sendNewEvent', {op: 'NOTE_UPDATE', data: {id: $scope.note.id, name: $scope.note.name}});
    }
  };

  /** update the current note */
  $rootScope.$on('setNoteContent', function(event, note) {
    $scope.paragraphUrl = $routeParams.paragraphId;
    $scope.asIframe = $routeParams.asIframe;
    if ($scope.paragraphUrl) {
      note = cleanParagraphExcept($scope.paragraphUrl, note);
      $rootScope.$emit('setIframe', $scope.asIframe);
    }
    if ($scope.note === null) {
      $scope.note = note;
    } else {
      updateNote(note);
    }
  });
  
  var cleanParagraphExcept = function(paragraphId, note) {
    var noteCopy = {};
    noteCopy.id = note.id;
    noteCopy.name = note.name;
    noteCopy.paragraphs = [];
    for (var i=0; i<note.paragraphs.length; i++) {
      if (note.paragraphs[i].id === paragraphId) {
        noteCopy.paragraphs[0] = note.paragraphs[i];
        if (!noteCopy.paragraphs[0].config) {
          noteCopy.paragraphs[0].config = {};
        }
        noteCopy.paragraphs[0].config.editorHide = true;
        noteCopy.paragraphs[0].config.asIframe = true;
        break;
      }
    }
    return noteCopy;
  };

  $rootScope.$on('moveParagraphUp', function(event, paragraphId) {
    var newIndex = -1;
    for (var i=0; i<$scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i-1;
        break;
      }
    }

    if (newIndex<0 || newIndex>=$scope.note.paragraphs.length) {
      return;
    }

    $rootScope.$emit('sendNewEvent', { op: 'MOVE_PARAGRAPH', data : {id: paragraphId, index: newIndex}});
  });

  $rootScope.$on('moveParagraphDown', function(event, paragraphId) {
    var newIndex = -1;
    for (var i=0; i<$scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i+1;
        break;
      }
    }

    if (newIndex<0 || newIndex>=$scope.note.paragraphs.length) {
      return;
    }

    $rootScope.$emit('sendNewEvent', { op: 'MOVE_PARAGRAPH', data : {id: paragraphId, index: newIndex}});
  });

  $rootScope.$on('moveFocusToPreviousParagraph', function(event, currentParagraphId){
    var focus = false;
    for (var i=$scope.note.paragraphs.length-1; i>=0; i--) {
      if (focus === false ) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
            focus = true;
            continue;
        }
      } else {
        var p = $scope.note.paragraphs[i];
        if (!p.config.hide && !p.config.editorHide) {
          $rootScope.$emit('focusParagraph', $scope.note.paragraphs[i].id);
          break;
        }
      }
    }
  });

  $rootScope.$on('moveFocusToNextParagraph', function(event, currentParagraphId){
    var focus = false;
    for (var i=0; i<$scope.note.paragraphs.length; i++) {
      if (focus === false ) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
            focus = true;
            continue;
        }
      } else {
        var p = $scope.note.paragraphs[i];
        if (!p.config.hide && !p.config.editorHide) {
          $rootScope.$emit('focusParagraph', $scope.note.paragraphs[i].id);
          break;
        }
      }
    }
  });

  var updateNote = function(note) {
    /** update Note name */
    if (note.name !== $scope.note.name) {
      console.log('change note name: %o to %o', $scope.note.name, note.name);
      $scope.note.name = note.name;
    }

    /** add new paragraphs */
    var idx = 0;
    note.paragraphs.forEach(function(newEntry) {
      var found = false;
      for (var oldIdx=0; oldIdx< $scope.note.paragraphs.length; oldIdx++) {
        if ($scope.note.paragraphs[oldIdx].id === newEntry.id) {
          found = true;
          break;
        }
      };

      if (found) {
        if (idx === oldIdx) {
          $rootScope.$emit('updateParagraph', {paragraph: newEntry});
        } else {
          // move paragraph
          $scope.note.paragraphs.splice(oldIdx, 1);
          $scope.note.paragraphs.splice(idx, 0, newEntry);
        }
      } else {
        // insert new paragraph
        $scope.note.paragraphs.splice(idx, 0, newEntry);
      }
      idx++;
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
