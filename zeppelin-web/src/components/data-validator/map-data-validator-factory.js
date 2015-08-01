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

angular.module('zeppelinWebApp').factory('mapdataValidator', function($rootScope, DataValidator, dataModelSchemas) {

  var msg = '';
  var errorStatus = false;
  var mapdataValidator = new DataValidator(dataModelSchemas.MapSchema);
  mapdataValidator.checkLatiLong = function() {
    //TODO add mapDataCheckValidator and latitudeValidator
  };


  function mapDataCheckValidator(record, schema) {
    if (record) {
      for (var i = 0; i < schema.type.length; i++) {
        if (isNaN(record[i]) !== (schema.type[i] === 'string')) {
          errorStatus = true;
          msg += 'data record ' + (record[i]) + ' is not matching for schema | ';
          return true;
        }else{
          if(i===2){
            errorStatus = !latitudeValidator(record[i],schema.range);
          }if(i===3){
            errorStatus = !longitudeValidator(record[i],schema.range);
          }
          if(errorStatus){
            return true;
          }
        }
      }//end validation on data record
      errorStatus = false;
      return false;
    } else {
      msg += 'data record does not exisiting | ';
      return true;
    }
  }

  //Latitude measurements range from 0° to (+/–)90°.
  function latitudeValidator(record, schema) {
    if(record) {
      var latitude = parseFloat(record);
      if(schema.latitude.low <= latitude <= schema.latitude.high) {
        return true;
      }
      msg += 'Latitude ' + record + ' is not in range | ';
      return false;
    } else {
      msg += 'Latitude record does not exisiting | ';
      return true;
    }
  }

  //Longitude measurements range from 0° to (+/–)180°.
  function longitudeValidator(record, schema) {
    console.log(record);
    if(record) {
      var longitude = parseFloat(record);
      if(schema.longitude.low <= longitude <= schema.longitude.high) {
        return true;
      }
      msg += 'Longitude' + record + ' is not in range | ';
      return false;
    } else {
      msg += 'Longitude record does not exisiting | ';
      return true;
    }
  }

  return mapdataValidator;
});
