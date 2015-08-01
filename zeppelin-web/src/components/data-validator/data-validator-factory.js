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

angular.module('zeppelinWebApp').factory('DataValidator', function($rootScope) {

  var msg = '';
  var errorStatus = false;
  var DataValidator = function(schema) {
    this.schema = schema;
    this.error = getErrorStatus;
    this.checkData = checkData;
    this.getMsg = getMsg;
    this.data = null;
  };


  function checkData() {
    if (basicCheck(this.data, this.schema)) {// jshint ignore:line
      msg += 'data is exisiting | ';
    } else {
      msg += 'data does not exisiting | ';
    }
  }

  function getMsg() {
    return msg;
  }

  function getErrorStatus() {
    return errorStatus;
  }

  function basicCheck(data, schema) {
    if (data.code && data.rows) {
      rowCheck(data.rows, 3, schema);
      return true;
    } else {
      msg += 'data rows does not exisiting | ';
      return false;
    }
  }

  function rowCheck(rowData, num, schema) {
    if (rowData) {
      for (var i = 0; i < rowData.length; i++) {
        var row = rowData[i];
        if (dataCheckValidator(row, schema)) {
          msg += 'data record does not mapping to data schema| ';
        }
      }
      return true;
    } else {
      msg += 'data row does not exisiting | ';
      return false;
    }
  }

  function dataCheckValidator(record, schema) {
    if (record) {
      for (var i = 0; i < schema.type.length; i++) {
        if (isNaN(record[i]) !== (schema.type[i] === 'string')) {
          errorStatus = true;
          msg += 'data record ' + (record[i]) + ' is not matching for schema | ';
          return true;
        }
      }//end validation on data record
      errorStatus = false;
      return false;
    } else {
      msg += 'data record does not exisiting | ';
      return true;
    }
  }

  return DataValidator;
});
