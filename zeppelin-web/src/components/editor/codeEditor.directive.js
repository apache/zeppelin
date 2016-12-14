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
(function() {

  angular.module('zeppelinWebApp').directive('codeEditor', codeEditor);

  function codeEditor($templateRequest, $compile) {
    return {
      restrict: 'AE',
      scope: {
        paragraphId: '=paragraphId',
        paragraph: '=paragraphContext',
        dirtyText: '=dirtyText',
        originalText: '=originalText',
        onLoad: '=onLoad'
      },
      link: function(scope, element, attrs, controller) {
        $templateRequest('components/editor/ace.editor.directive.html').then(function(editorHtml) {
          var editor = angular.element(editorHtml);
          editor.attr('id', scope.paragraphId + '_editor');
          element.append(editor);
          $compile(editor)(scope);
        });
      }
    };
  }

})();
