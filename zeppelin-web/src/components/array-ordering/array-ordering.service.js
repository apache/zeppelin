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

angular.module('zeppelinWebApp').service('arrayOrderingSrv', ArrayOrderingService)

function ArrayOrderingService(TRASH_FOLDER_ID) {
  'ngInject'

  let arrayOrderingSrv = this

  this.noteListOrdering = function (note) {
    if (note.id === TRASH_FOLDER_ID) {
      return '\uFFFF'
    }
    return arrayOrderingSrv.getNoteName(note)
  }

  this.getNoteName = function (note) {
    if (note.name === undefined || note.name.trim() === '') {
      return 'Note ' + note.id
    } else {
      return note.name
    }
  }

  this.noteComparator = function (v1, v2) {
    let note1 = v1.value || v1
    let note2 = v2.value || v2

    if (note1.id === TRASH_FOLDER_ID) {
      return 1
    }

    if (note2.id === TRASH_FOLDER_ID) {
      return -1
    }

    if (note1.children === undefined && note2.children !== undefined) {
      return 1
    }

    if (note1.children !== undefined && note2.children === undefined) {
      return -1
    }

    let noteName1 = arrayOrderingSrv.getNoteName(note1)
    let noteName2 = arrayOrderingSrv.getNoteName(note2)

    return noteName1.localeCompare(noteName2)
  }
}
