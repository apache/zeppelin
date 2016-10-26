/*
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

// jscs:disable requireCamelCaseOrUpperCaseIdentifiers
/*eslint-disable camelcase*/
/*eslint-disable no-undef*/
(function() {
  var jsonEditor = angular.module('restful-editor', []);
  jsonEditor.directive('restfulEditor', function() {
    return {
      restrict: 'A',
      scope: {
        model: '=',
        options: '='
      },
      link: function(scope, element, attrs) {
        var id = 'jsoneditor' + Math.floor(Math.random() * 100000000);
        //console.log('ID: '+id);
        element.attr('id', id);
        attrs.$set('disabled', 'disabled');

        scope.options = scope.options || {};
        scope.options.change = function() {
          setTimeout(function() {
            scope.$apply(function() {
              if (scope.editor !== undefined) {
                scope.model = scope.editor.get();
              }
            });
          });
        };
        scope.editor;
        scope.setup = {};
        scope.setup = angular.extend(scope.setup, scope.options);
        scope.model = scope.model || {};
        scope.container = document.getElementById(id);
        scope.editor = new JSONEditor(scope.container, scope.setup, scope.model);
        scope.globalTimeout = null;
        scope.delay_timeout = function(second_time, func) {
          if (scope.globalTimeout !== null) {
            clearTimeout(scope.globalTimeout);
          }
          scope.globalTimeout = setTimeout(function() {
            func();
          }, second_time);
        };
        // Update editor when change model
        scope.$watch(function() {
          return scope.model;
        }, function() {
          scope.delay_timeout(1000, function() {
            //console.log('Change on '+id);
            var json1 = angular.toJson(scope.model);
            var json2 = angular.toJson(scope.editor.get());
            if (!angular.equals(json1, json2)) {
              scope.editor.set(scope.model);
            }
          });
        }, true);
        scope.$watchCollection(function() {
          return scope.model;
        }, function() {
          scope.delay_timeout(1000, function() {
            ////console.log('Change on '+id);
            var json1 = angular.toJson(scope.model);
            var json2 = angular.toJson(scope.editor.get());
            if (!angular.equals(json1, json2)) {
              scope.editor.set(scope.model);
            }
          });
        });

        // Update editor when change options
        scope.$watch(function() {
          return scope.options;
        }, function(newValue, oldValue) {
          scope.setup = angular.extend(scope.setup, scope.options);
          for (var k in newValue) {
            if (newValue.hasOwnProperty(k)) {
              var v = newValue[k];
              if (newValue[k] !== oldValue[k]) {
                if (k === 'mode') {
                  scope.editor.setMode(v);
                } else if (k === 'name') {
                  scope.editor.setName(v);
                } else if (k === 'theme') {
                  scope.editor.setTheme(v);
                } else if (k === 'search') {
                  scope.editor.setOption('search', v);
                } else if (k === 'expand') {
                  if (v === true) {
                    scope.editor.collapseAll();
                  } else {
                    scope.editor.expandAll();
                  }
                } else { //other settings cannot be changed without re-creating the JsonEditor
                  //scope.editor = new JSONEditor(scope.container, scope.setup,scope.model);
                  //return;
                }
              }
            }
          }
        }, true);
      }
    };
  });
})();
