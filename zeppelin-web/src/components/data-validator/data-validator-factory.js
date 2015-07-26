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

angular.module('zeppelinWebApp').factory('dataValidator', function($rootScope) {

  var dataValidator = function() {
    this.schema = null;
    this.error = true;
    this.msg = null;
    this.checkData = checkData;
  };

  //TODO - data validation process inprogress.
  function checkData(data) {
    if (basicCheck(data)) {
      this.error = false;
    }
  };

  function basicCheck(data) {
    if (data.code) {
      return true;
    } else {
      return false;
    }
  }

  return dataValidator;
});
