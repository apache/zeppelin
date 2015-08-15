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
    basicCheck();
  };

  function rowCheck(dataRows) {
    if(dataRows instanceof Object) {
      for(var key in dataRows) {
        schemaChecker(key, 0);
        if(dataRows.hasOwnProperty(key)) {
          var obj = dataRows[key];
          rowValueCheck(obj);
        }
      }
    } else {
      markValidationError('dataRows is not a Object | ');
    }
  }

  function rowValueCheck(record) {
    if(record instanceof Object) {
      //console.log(record)
      for(var key in record) {
        var recordValues = record[key];
        var countKey = 1;
        for(var recordKey in recordValues) {

          if(recordValues.hasOwnProperty(recordKey)) {
            var values = recordValues[recordKey];
            schemaChecker(values, countKey);
            countKey++;
          }
        }
      }
    } else {
      markValidationError('record is not a Object | ');
    }
  }

  function basicCheck() {
    var data = chartdataValidator.data;
    if(data.schema && data.rows) {
      rowCheck(data.rows);
      console.log(data);
      return true;
    } else {
      markValidationError('data rows does not exisiting | ');
    }
  }

  function schemaChecker(record, index) {
    if(isNaN(record) !== (simpleChartSchema.type[index] === 'string')) {
      markValidationError('data record ' + record +
        ' is not matching for schema | ');
    }
    //console.log(record+' is passed');
  }

  function markValidationError(msg) {
    chartdataValidator.setMsg(msg);
    chartdataValidator.setError();
  }

  return chartdataValidator;
});