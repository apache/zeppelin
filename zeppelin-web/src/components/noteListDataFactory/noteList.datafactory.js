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
(function() {

  angular.module('zeppelinWebApp').factory('noteListDataFactory', noteListDataFactory);

  noteListDataFactory.$inject = ['TRASH_FOLDER_ID'];

  function noteListDataFactory(TRASH_FOLDER_ID) {
    var notes = {
      root: {children: []},
      flatList: [],
      flatFolderMap: {},

      setNotes: function(notesList) {
        // a flat list to boost searching
        notes.flatList = _.map(notesList, (note) => {
          note.isTrash = note.name ?
            note.name.split('/')[0] === TRASH_FOLDER_ID : false;
          return note;
        });

        // construct the folder-based tree
        notes.root = {children: []};
        notes.flatFolderMap = {};
        _.reduce(notesList, function(root, note) {
          var noteName = note.name || note.id;
          var nodes = noteName.match(/([^\/][^\/]*)/g);

          // recursively add nodes
          addNode(root, nodes, note.id);

          return root;
        }, notes.root);
      }
    };

    var addNode = function(curDir, nodes, noteId) {
      if (nodes.length === 1) {  // the leaf
        curDir.children.push({
          name: nodes[0],
          id: noteId,
          path: curDir.id ? curDir.id + '/' + nodes[0] : nodes[0],
          isTrash: curDir.id ? curDir.id.split('/')[0] === TRASH_FOLDER_ID : false
        });
      } else {  // a folder node
        var node = nodes.shift();
        var dir = _.find(curDir.children,
          function(c) {return c.name === node && c.children !== undefined;});
        if (dir !== undefined) { // found an existing dir
          addNode(dir, nodes, noteId);
        } else {
          var newDir = {
            id: curDir.id ? curDir.id + '/' + node : node,
            name: node,
            hidden: true,
            children: [],
            isTrash: curDir.id ? curDir.id.split('/')[0] === TRASH_FOLDER_ID : false
          };

          // add the folder to flat folder map
          notes.flatFolderMap[newDir.id] = newDir;

          curDir.children.push(newDir);
          addNode(newDir, nodes, noteId);
        }
      }
    };

    return notes;
  }

})();
