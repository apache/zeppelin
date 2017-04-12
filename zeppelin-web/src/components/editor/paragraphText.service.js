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

angular.module('zeppelinWebApp').service('paragraphTextService', paragraphTextService);

export function paragraphTextService() {
  'ngInject'

  /** `note.paragraph.dirtyText` */
  const dirtyTextMap = {}

  this.setDirtyText = function(dirtyText, noteId, paragraphId) {
    if (!noteId || !paragraphId) { return }

    if (!dirtyTextMap[noteId]) { dirtyTextMap[noteId] = {} }
    dirtyTextMap[noteId][paragraphId] = dirtyText
  }

  this.getDirtyText = function(noteId, paragraphId) {
    if (!noteId || !paragraphId) { return undefined }

    if (typeof dirtyTextMap[noteId] === 'undefined') { return undefined }
    return dirtyTextMap[noteId][paragraphId]
  }
}
