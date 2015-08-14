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

angular.module('zeppelinWebApp').factory('chartdataValidator', function(
  $rootScope, DataValidator, dataModelSchemas) {

  var simpleChartSchema = dataModelSchemas.D3ChartDataSchema;
  var chartdataValidator = new DataValidator(simpleChartSchema);

  //overriding the check data 
  chartdataValidator.checkData = function() {
    console.log('over riding happen');
    basicCheck();
  };

  function rowCheck(dataRows) {
    if(dataRows instanceof Object) {
      console.log(dataRows)
      for(var key in dataRows) {
        key
        if(dataRows.hasOwnProperty(key)) {
          var obj = dataRows[key];
          rowValueCheck(obj);
        }
      }
    } else {
      msg += 'dataRows is not a Object | ';
      errorStatus = true;
    }
  }

  function rowValueCheck(record) {
    if(record instanceof Object) {
      console.log(record)
      for(var key in record) {

        var recordValues = record[key];
        for(var dataValues in recordValues) {
          if(dataValues.hasOwnProperty(key)) {
            var values = values[key];
            console.log(values);
          }
        }
      }
    } else {
      msg += 'record is not a Object | ';
      errorStatus = true;
    }
  }

  function basicCheck() {
    var data = chartdataValidator.data;
    if(data.schema && data.rows) {
      rowCheck(data.rows);
      console.log(data);
      return true;
    } else {
      msg += 'data rows does not exisiting | ';
      errorStatus = true;
    }
  }

  function schemaChecker(record, index) {
    if(isNaN(record) !== (schema.type[index] === 'string')) {
      errorStatus = true;
      msg += 'data record ' + (record[index]) +
        ' is not matching for schema | ';
      return true;
    }
  }


  return chartdataValidator;
});