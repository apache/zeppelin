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

angular.module('zeppelinWebApp').factory('noteListFactory', NoteListFactory)

function NoteListFactory(arrayOrderingSrv, TRASH_FOLDER_ID) {
  'ngInject'

  const notes = {
    root: {children: []},
    flatList: [],
    flatFolderMap: {},

    setNotes: function (notesList) {
      // a flat list to boost searching
      notes.flatList = _.map(notesList, (note) => {
        note.isTrash = note.name
          ? note.name.split('/')[0] === TRASH_FOLDER_ID : false
        return note
      })

      // construct the folder-based tree
      notes.root = {children: []}
      notes.flatFolderMap = {}
      _.reduce(notesList, function (root, note) {
        let noteName = note.name || note.id
        let nodes = noteName.match(/([^\/][^\/]*)/g)

        // recursively add nodes
        addNode(root, nodes, note.id)

        return root
      }, notes.root)
      notes.root.children.sort(arrayOrderingSrv.noteComparator)
    }
  }

  const addNode = function (curDir, nodes, noteId) {
    if (nodes.length === 1) {  // the leaf
      curDir.children.push({
        name: nodes[0],
        id: noteId,
        path: curDir.id ? curDir.id + '/' + nodes[0] : nodes[0],
        isTrash: curDir.id ? curDir.id.split('/')[0] === TRASH_FOLDER_ID : false
      })
    } else {  // a folder node
      let node = nodes.shift()
      let dir = _.find(curDir.children,
        function (c) { return c.name === node && c.children !== undefined })
      if (dir !== undefined) { // found an existing dir
        addNode(dir, nodes, noteId)
      } else {
        let newDir = {
          id: curDir.id ? curDir.id + '/' + node : node,
          name: node,
          hidden: true,
          children: [],
          isTrash: curDir.id ? curDir.id.split('/')[0] === TRASH_FOLDER_ID : false
        }

        // add the folder to flat folder map
        notes.flatFolderMap[newDir.id] = newDir

        curDir.children.push(newDir)
        addNode(newDir, nodes, noteId)
      }
    }
  }

  return notes
}
