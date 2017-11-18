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

import './dynamic-forms.css'

angular.module('zeppelinWebApp').directive('dynamicForms', DynamicFormDirective)

function DynamicFormDirective($templateRequest, $compile) {
  return {
    restrict: 'AE',
    scope: {
      id: '=id',
      hide: '=hide',
      disable: '=disable',
      actiononchange: '=actiononchange',
      forms: '=forms',
      params: '=params',
      action: '=action',
      removeaction: '=removeaction'
    },

    link: function (scope, element, attrs, controller) {
      scope.loadForm = this.loadForm
      scope.toggleCheckbox = this.toggleCheckbox
      $templateRequest('app/notebook/dynamic-forms/dynamic-forms.directive.html').then(function (formsHtml) {
        let forms = angular.element(formsHtml)
        element.append(forms)
        $compile(forms)(scope)
      })
    },

    loadForm: function (formulaire, params) {
      let value = formulaire.defaultValue
      if (params[formulaire.name]) {
        value = params[formulaire.name]
      }

      params[formulaire.name] = value
    },

    toggleCheckbox: function (formulaire, option, params) {
      let idx = params[formulaire.name].indexOf(option.value)
      if (idx > -1) {
        params[formulaire.name].splice(idx, 1)
      } else {
        params[formulaire.name].push(option.value)
      }
    }

  }
}
