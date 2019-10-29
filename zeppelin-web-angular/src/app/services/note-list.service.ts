/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Inject, Injectable } from '@angular/core';

import { TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';
import { NotesInfoItem } from '@zeppelin/sdk';

import { NodeList } from '../interfaces/node-list';
import { ArrayOrderingService } from './array-ordering.service';

@Injectable({
  providedIn: 'root'
})
export class NoteListService {
  notes: NodeList = {
    root: { children: [] },
    flatList: [],
    flatFolderMap: {}
  };

  setNotes(notesList: NotesInfoItem[]) {
    // a flat list to boost searching
    this.notes.flatList = notesList.map(note => {
      const isTrash = note.path ? note.path.split('/')[1] === this.TRASH_FOLDER_ID : false;
      return { ...note, isTrash };
    });

    // construct the folder-based tree
    this.notes.root = { children: [] };
    this.notes.flatFolderMap = {};
    notesList.reduce((root, note) => {
      const notePath = note.path || note.id;
      const nodes = notePath.match(/([^\/][^\/]*)/g);

      // recursively add nodes
      this.addNode(root, nodes, note.id);

      return root;
    }, this.notes.root);
    this.notes.root.children.sort(this.arrayOrderingService.noteComparator);
  }

  addNode(curDir, nodes, noteId) {
    if (nodes.length === 1) {
      // the leaf
      curDir.children.push({
        id: noteId,
        title: nodes[0],
        isLeaf: true,
        nodeType: 'note',
        path: curDir.id ? `${curDir.id}/${nodes[0]}` : nodes[0],
        isTrash: curDir.id ? curDir.id.split('/')[0] === this.TRASH_FOLDER_ID : false
      });
    } else {
      // a folder node
      const node = nodes.shift();
      const dir = curDir.children.find(c => {
        return c.title === node && c.children !== undefined;
      });
      if (dir !== undefined) {
        // found an existing dir
        this.addNode(dir, nodes, noteId);
      } else {
        const id = curDir.id ? `${curDir.id}/${node}` : node;
        const newDir = {
          id,
          title: node,
          expanded: false,
          nodeType: id === this.TRASH_FOLDER_ID ? 'trash' : 'folder',
          children: [],
          isTrash: curDir.id ? curDir.id.split('/')[0] === this.TRASH_FOLDER_ID : false
        };

        // add the folder to flat folder map
        this.notes.flatFolderMap[newDir.id] = newDir;

        curDir.children.push(newDir);
        this.addNode(newDir, nodes, noteId);
      }
    }
  }

  constructor(
    @Inject(TRASH_FOLDER_ID_TOKEN) public TRASH_FOLDER_ID: string,
    private arrayOrderingService: ArrayOrderingService
  ) {}
}
