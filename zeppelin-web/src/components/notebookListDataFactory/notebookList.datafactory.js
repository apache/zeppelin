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

angular.module('zeppelinWebApp').factory('notebookListDataFactory', function() {
  var notes = {};

  notes.list = [];
  notes.flatList = [];

  notes.setNotes = function(notesList) {
    notes.flatList = angular.copy(notesList); // a flat list to boost searching

    // construct the folder-based list
    notes.list = [];
    for (var i in notesList) {
      var note = notesList[i];
      var noteName = note.name || note.id;
      var dirs = noteName.match(/([^\\\][^\/]|\\\/)+/g);

      var curDir = notes.list;
      for (var t = 0; t < dirs.length; t++) {
        var dir = dirs[t];
        if (t == dirs.length - 1) { // last item
          curDir.push({
            name : dir,
            id : note.id
          });
        } else {
          var child = null;
          for (var j in curDir) {
            if (curDir[j].name === dir && 'children' in curDir[j]) {
              child = curDir[j];
              break;
            }
          }
          if (child === null) {
            child = {
              name : dir,
              hidden : true,
              children : []
            };
            curDir.push(child);
          }
          curDir = child.children;
        }
      }
    }
  };

  return notes;
});
