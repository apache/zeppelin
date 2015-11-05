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

angular.module('zeppelinWebApp').factory('notebookListDataFactory', function($location) {
  var notes = {};

  notes.list = [];

  notes.setNotes = function(notesList) {
  	if(notes.list.length!==0 && notes.list.length+1===notesList.length){
		var newNoteID = getNewNoteID(notesList);
		$location.path('/notebook/'+newNoteID);
    }
    notes.list = angular.copy(notesList);
  };

  function getNewNoteID(newNotes){
    var currentNotes = notes.list.map(function(note){return note.id;});
    var newestNote = newNotes.filter(function(note){
      if(currentNotes.indexOf(note.id)===-1){
        return true;
      }
    });
    return newestNote[0].id;
  }

  return notes;
});