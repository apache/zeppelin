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

angular.module('zeppelinWebApp').service('dataValidatorSrv', function(
  $rootScope, mapdataValidator, scatterdataValidator, chartdataValidator,
  dataModelSchemas) {

  this.validateMapData = function(data) {
    var mapValidator = mapdataValidator;
    doBasicCheck(mapValidator, data);
    mapValidator.checkLatiLong();
    return buildMsg(mapValidator);
  };

  this.validateChartData = function(data) {
    var chartValidator = chartdataValidator;
    doBasicCheck(chartValidator, data);
    return buildMsg(chartValidator);
  };

  this.validateScatterData = function(data) {
    var scatterChartValidator = scatterdataValidator;
    doBasicCheck(scatterChartValidator, data);
    return buildMsg(scatterChartValidator);
  };

  function buildMsg(validator) {
    var msg = {
      'error': validator.error(),
      'msg': validator.getMsg()
    };
    return msg;
  }

  function doBasicCheck(validator, data) {
    validator.clearMsg();
    validator.data = data;
    validator.checkData();
  }

});