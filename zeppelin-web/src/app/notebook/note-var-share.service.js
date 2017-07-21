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

angular.module('zeppelinWebApp').service('noteVarShareService', NoteVarShareService)

function NoteVarShareService () {
  'ngInject'

  let store = {}

  this.clear = function () {
    store = {}
  }

  this.put = function (key, value) {
    store[key] = value
  }

  this.get = function (key) {
    return store[key]
  }

  this.del = function (key) {
    let v = store[key]
    delete store[key]
    return v
  }
}
