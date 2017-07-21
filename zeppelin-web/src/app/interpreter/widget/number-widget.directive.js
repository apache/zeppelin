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

angular.module('zeppelinWebApp').directive('numberWidget', InterpreterNumberDirective)

function InterpreterNumberDirective() {
  return {
    require: 'ngModel',
    link: function (scope, element, attrs, modelCtrl) {
      modelCtrl.$parsers.push(function (inputValue) {
        let transformedInput = inputValue ? inputValue.replace(/[^\d.-]/g, '') : null
        if (transformedInput !== inputValue) {
          modelCtrl.$setViewValue(transformedInput)
          modelCtrl.$render()
        }
        return transformedInput
      })
    }
  }
}
