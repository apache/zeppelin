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
  notes.root = {
    children : []
  };
  notes.flatList = [];

  notes.setNotes = function(notesList) {
    // a flat list to boost searching
    notes.flatList = angular.copy(notesList);

    // construct the folder-based tree
    _.reduce(notesList, function(root, note) {
      var noteName = note.name || note.id;
      var nodes = noteName.match(/([^\\\][^\/]|\\\/)+/g);

      // recursively add nodes
      var addNode = function(curDir, nodes) {
        if (nodes.length === 1) {  // the leaf
          curDir.children.push({
            name : nodes[0],
            id : note.id
          });
        } else {  // a folder node
          var node = nodes.shift();
          var dir = _.find(curDir.children,
            function(c) {return c.name === node && c.children !== undefined;});
          if (dir !== undefined) { // found an existing dir
            addNode(dir, nodes);
          } else {
            var newDir = {
              name : node,
              hidden : true,
              children : []
            };
            curDir.children.push(newDir);
            addNode(newDir, nodes);
          }
        }
      };
      addNode(root, nodes);
      return root;
    }, notes.root);
  };

  return notes;
});
