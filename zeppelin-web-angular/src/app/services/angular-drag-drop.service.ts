/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Injectable } from '@angular/core';
import * as angular from 'angular';
import * as JQuery from 'jquery';

interface CustomDragDropService {
  draggableScope: angular.IScope | null;
  dragData: unknown | null;
  dragIndex: number | null;
  dragModelPath: null | string;
  dragSettings: { placeholder?: string } | null;
  draggableElement: HTMLElement | null;

  callEventCallback(scope: angular.IScope, callbackName: string, event: DragEvent, ui?: UI): void;
  updateModel(scope: angular.IScope, modelPath: string, newValue: unknown, index?: number): void;
  removeFromModel(scope: angular.IScope, modelPath: string, index?: number): void;
}

interface UI {
  draggable: JQuery<HTMLElement> | null;
  helper: null;
  position: { top: number; left: number };
  offset: { top: number; left: number };
}

@Injectable({
  providedIn: 'root'
})
export class AngularDragDropService {
  constructor() {}

  addDragDropDirectives(module: angular.IModule): void {
    // Drag and drop service to maintain state across directives
    module.factory('customDragDropService', [
      '$parse',
      function($parse: angular.IParseService): CustomDragDropService {
        return {
          draggableScope: null,
          dragData: null,
          dragIndex: null,
          dragModelPath: null,
          dragSettings: null,
          draggableElement: null,

          callEventCallback: function(scope, callbackStr, event, ui) {
            if (!callbackStr) {
              return;
            }

            const { targetCallback, targetScope, args: extractedArgs } = extract(callbackStr);
            const fullArgs = [event, ui].concat(extractedArgs);

            if (typeof targetCallback === 'function') {
              return targetCallback.apply(targetScope, fullArgs);
            }

            function extract(_callbackStr: string) {
              const atStartBracket = _callbackStr.indexOf('(') !== -1 ? _callbackStr.indexOf('(') : _callbackStr.length;
              const atEndBracket =
                _callbackStr.lastIndexOf(')') !== -1 ? _callbackStr.lastIndexOf(')') : _callbackStr.length;

              const argsString = _callbackStr.substring(atStartBracket + 1, atEndBracket);
              const identifierTokens = argsString ? argsString.split(',') : [];
              const args = identifierTokens.map(item => $parse(item.trim())(scope));

              const constructorName =
                _callbackStr.indexOf('.') !== -1 ? _callbackStr.substr(0, _callbackStr.indexOf('.')) : null;
              // @ts-ignore
              const constructorCandid = constructorName && scope[constructorName];
              const constructor =
                constructorCandid && typeof constructorCandid.constructor === 'function' ? constructorCandid : null;

              const callbackName = _callbackStr.substring((constructor && constructor.length + 1) || 0, atStartBracket);
              // @ts-ignore
              const callbackCandid = scope[callbackName];
              // If the expression is a method call, then the parsed constructor becomes its bound scope.
              const _scope = callbackCandid ? scope : constructor;
              const callback = callbackCandid || constructor[callbackName];

              return {
                args,
                targetScope: _scope,
                targetCallback: callback
              };
            }
          },

          updateModel: function(scope, modelPath, newValue, index) {
            const getter = $parse(modelPath);
            const setter = getter.assign;
            const modelValue = getter(scope);

            if (angular.isArray(modelValue)) {
              if (typeof index === 'number') {
                modelValue[index] = newValue;
              } else {
                modelValue.push(newValue);
              }
            } else {
              if (setter) {
                setter(scope, newValue);
              }
            }

            scope.$apply();
          },

          removeFromModel: function(scope, modelPath, index) {
            const getter = $parse(modelPath);
            const modelValue = getter(scope);

            if (angular.isArray(modelValue) && typeof index === 'number') {
              modelValue.splice(index, 1);
              scope.$apply();
            }
          }
        };
      }
    ]);

    // jqyoui-draggable directive
    module.directive('jqyouiDraggable', [
      'customDragDropService',
      function(dragDropService: CustomDragDropService) {
        return {
          restrict: 'A',
          link: function(scope, element, attrs) {
            const el = element[0];

            // Check if dragging is enabled
            const isDragEnabled = function() {
              return attrs.drag === 'true' || scope.$eval(attrs.drag) === true;
            };

            // Make element draggable when enabled
            const updateDraggable = function() {
              el.draggable = isDragEnabled();
            };

            updateDraggable();

            // Watch for changes in drag attribute
            scope.$watch(function() {
              return scope.$eval(attrs.drag);
            }, updateDraggable);

            el.addEventListener('dragstart', function(event) {
              if (!isDragEnabled()) {
                event.preventDefault();
                return;
              }

              const dragSettings = scope.$eval(attrs.jqyouiDraggable) || {};
              const ngModel = attrs.ngModel;

              if (ngModel) {
                dragDropService.draggableScope = scope;
                dragDropService.dragIndex = dragSettings.index;
                dragDropService.dragModelPath = ngModel;
                dragDropService.dragSettings = dragSettings;
                dragDropService.draggableElement = el;

                const modelValue = scope.$eval(ngModel);
                if (angular.isArray(modelValue) && typeof dragSettings.index === 'number') {
                  dragDropService.dragData = angular.copy(modelValue[dragSettings.index]);
                } else {
                  dragDropService.dragData = angular.copy(modelValue);
                }

                // Store data in dataTransfer for compatibility
                if (event.dataTransfer) {
                  event.dataTransfer.setData(
                    'text/plain',
                    JSON.stringify({
                      data: dragDropService.dragData,
                      index: dragDropService.dragIndex,
                      modelPath: ngModel
                    })
                  );
                  event.dataTransfer.effectAllowed = 'move';
                }
              }

              // Add visual feedback
              el.style.opacity = '0.5';

              // Call onStart callback if provided
              if (dragSettings.onStart) {
                const ui: UI = {
                  draggable: angular.element(el),
                  helper: null,
                  position: { top: event.clientY, left: event.clientX },
                  offset: { top: event.pageY, left: event.pageX }
                };
                dragDropService.callEventCallback(scope, dragSettings.onStart, event, ui);
              }
            });

            el.addEventListener('dragend', function(event) {
              // Remove visual feedback
              el.style.opacity = '';

              const dragSettings = scope.$eval(attrs.jqyouiDraggable) || {};

              // Call onStop callback if provided
              if (dragSettings.onStop) {
                const ui = {
                  draggable: angular.element(el),
                  helper: null,
                  position: { top: event.clientY, left: event.clientX },
                  offset: { top: event.pageY, left: event.pageX }
                };
                dragDropService.callEventCallback(scope, dragSettings.onStop, event, ui);
              }
            });
          }
        };
      }
    ]);

    // jqyoui-droppable directive
    module.directive('jqyouiDroppable', [
      'customDragDropService',
      function(dragDropService: CustomDragDropService) {
        return {
          restrict: 'A',
          link: function(scope, element, attrs) {
            const el = element[0];

            // Check if dropping is enabled
            const isDropEnabled = function() {
              return attrs.drop === 'true' || scope.$eval(attrs.drop) === true;
            };

            el.addEventListener('dragover', function(event) {
              if (!isDropEnabled()) {
                return;
              }

              event.preventDefault();
              if (event.dataTransfer) {
                event.dataTransfer.dropEffect = 'move';
              }

              // Add visual feedback
              el.style.backgroundColor = '#f0f0f0';
            });

            el.addEventListener('dragleave', function() {
              // Remove visual feedback
              el.style.backgroundColor = '';
            });

            el.addEventListener('drop', function(event) {
              if (!isDropEnabled()) {
                return;
              }

              event.preventDefault();

              // Remove visual feedback
              el.style.backgroundColor = '';

              const dropSettings = scope.$eval(attrs.jqyouiDroppable) || {};
              const dropModel = attrs.ngModel;

              if (!dropModel || !dragDropService.dragData) {
                return;
              }

              // Get drag data
              const dragData = dragDropService.dragData;
              const dragIndex = dragDropService.dragIndex;
              const draggableScope = dragDropService.draggableScope;
              const dragModelPath = dragDropService.dragModelPath;

              // Handle different drop scenarios
              if (dropSettings.multiple) {
                // Multiple items can be dropped - add to array
                dragDropService.updateModel(scope, dropModel, dragData);
              } else {
                // Single item drop - replace existing value
                const dropIndex = dropSettings.index;
                if (typeof dropIndex === 'number') {
                  dragDropService.updateModel(scope, dropModel, dragData, dropIndex);
                } else {
                  dragDropService.updateModel(scope, dropModel, dragData);
                }
              }

              // Remove from source if it's a move operation (not copy)
              // Check placeholder setting to determine if we should remove from source
              const dragSettings: CustomDragDropService['dragSettings'] = dragDropService.dragSettings || {};
              const shouldRemoveFromSource =
                draggableScope &&
                typeof dragIndex === 'number' &&
                dragModelPath &&
                dragSettings.placeholder !== 'keep' &&
                !dropSettings.deepCopy;

              if (shouldRemoveFromSource && draggableScope) {
                dragDropService.removeFromModel(draggableScope, dragModelPath ?? '', dragIndex ?? undefined);
              }

              // Call onDrop callback if provided
              if (dropSettings.onDrop) {
                // Create ui object similar to jQuery UI for compatibility
                const ui = {
                  draggable: dragDropService.draggableElement
                    ? angular.element(dragDropService.draggableElement)
                    : null,
                  helper: null, // HTML5 drag doesn't have helper concept
                  position: { top: event.clientY, left: event.clientX },
                  offset: { top: event.pageY, left: event.pageX }
                };

                dragDropService.callEventCallback(scope, dropSettings.onDrop, event, ui);
              }

              // Clear drag data
              dragDropService.dragData = null;
              dragDropService.dragIndex = null;
              dragDropService.draggableScope = null;
              dragDropService.dragModelPath = null;
              dragDropService.dragSettings = null;
              dragDropService.draggableElement = null;
            });
          }
        };
      }
    ]);
  }
}
