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

  function checkData(data, schema) {
    console.log(schema);
    if (basicCheck(data, schema)) {
      return false;
    }
  }

  function basicCheck(data, schema) {
    if (data.code && data.rows) {
      rowCheck(data.rows, 3, schema);
      return true;
    } else {
      return false;
    }
  }

  function rowCheck(rowData, num, schema) {
    if (rowData) {
      for (var i = 0; i < rowData.length; i++) {
        var row = rowData[i];
        dataCheckValidator(row, schema);
      }
      return true;
    } else {
      return false;
    }
  }

  function dataCheckValidator(record, schema) {
    console.log(schema.type.length);
    if (record) {
      for (var i = 0; i < schema.type.length; i++) {
        //TODOto remove logs code inprogress on return msg
        console.log('data is validated');
        console.log(record[i]);
        console.log(isNaN(record[i]) === (schema.type[i] === 'string'));
      }
      return true;
    } else {
      return false;
    }
  }

  return dataValidator;
});
