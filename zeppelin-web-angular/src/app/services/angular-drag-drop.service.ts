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

@Injectable({
  providedIn: 'root'
})
export class AngularDragDropService {
  constructor() {}

  // tslint:disable-next-line:no-any
  addDragDropDirectives(module: any): void {
    // Drag and drop service to maintain state across directives
    // tslint:disable-next-line:no-any
    module.service('customDragDropService', [
      '$parse',
      // tslint:disable-next-line:no-any
      function($parse: any) {
        this.draggableScope = null;
        this.dragData = null;
        this.dragIndex = null;
        this.dragModelPath = null;
        this.dragSettings = null;
        this.draggableElement = null;

        // tslint:disable-next-line:no-any
        this.callEventCallback = function(scope: any, callbackName: string, event: any, ui?: any) {
          if (!callbackName) {
            return;
          }

          const objExtract = extract(callbackName);
          const callback = objExtract.callback;
          const constructor = objExtract.constructor;
          const args = [event, ui].concat(objExtract.args);

          // call either $scope method or constructor's method
          const targetScope = scope[callback] ? scope : scope[constructor];
          const targetCallback = scope[callback] || scope[constructor][callback];

          if (typeof targetCallback === 'function') {
            return targetCallback.apply(targetScope, args);
          }

          function extract(_callbackName: string) {
            const atStartBracket =
              _callbackName.indexOf('(') !== -1 ? _callbackName.indexOf('(') : _callbackName.length;
            const atEndBracket =
              _callbackName.lastIndexOf(')') !== -1 ? _callbackName.lastIndexOf(')') : _callbackName.length;
            const argsString = _callbackName.substring(atStartBracket + 1, atEndBracket);
            let _constructor =
              _callbackName.indexOf('.') !== -1 ? _callbackName.substr(0, _callbackName.indexOf('.')) : null;
            _constructor =
              scope[_constructor] && typeof scope[_constructor].constructor === 'function' ? _constructor : null;

            return {
              callback: _callbackName.substring((_constructor && _constructor.length + 1) || 0, atStartBracket),
              // tslint:disable-next-line:no-any
              args: argsString ? argsString.split(',').map((item: string) => $parse(item.trim())(scope)) : [],
              constructor: _constructor
            };
          }
        };

        // tslint:disable-next-line:no-any
        this.updateModel = function(scope: any, modelPath: string, newValue: any, index?: number) {
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
        };

        // tslint:disable-next-line:no-any
        this.removeFromModel = function(scope: any, modelPath: string, index: number) {
          const getter = $parse(modelPath);
          const modelValue = getter(scope);

          if (angular.isArray(modelValue) && typeof index === 'number') {
            modelValue.splice(index, 1);
            scope.$apply();
          }
        };
      }
    ]);

    // jqyoui-draggable directive
    // tslint:disable-next-line:no-any
    module.directive('jqyouiDraggable', [
      'customDragDropService',
      // tslint:disable-next-line:no-any
      function(dragDropService: any) {
        return {
          restrict: 'A',
          // tslint:disable-next-line:no-any
          link: function(scope: any, element: any, attrs: any) {
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

            el.addEventListener('dragstart', function(event: DragEvent) {
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
                const ui = {
                  draggable: angular.element(el),
                  helper: null,
                  position: { top: event.clientY, left: event.clientX },
                  offset: { top: event.pageY, left: event.pageX }
                };
                dragDropService.callEventCallback(scope, dragSettings.onStart, event, ui);
              }
            });

            el.addEventListener('dragend', function(event: DragEvent) {
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
    // tslint:disable-next-line:no-any
    module.directive('jqyouiDroppable', [
      'customDragDropService',
      // tslint:disable-next-line:no-any
      function(dragDropService: any) {
        return {
          restrict: 'A',
          // tslint:disable-next-line:no-any
          link: function(scope: any, element: any, attrs: any) {
            const el = element[0];

            // Check if dropping is enabled
            const isDropEnabled = function() {
              return attrs.drop === 'true' || scope.$eval(attrs.drop) === true;
            };

            el.addEventListener('dragover', function(event: DragEvent) {
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

            el.addEventListener('dragleave', function(event: DragEvent) {
              // Remove visual feedback
              el.style.backgroundColor = '';
            });

            el.addEventListener('drop', function(event: DragEvent) {
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
              const dragSettings = dragDropService.dragSettings || {};
              const shouldRemoveFromSource =
                draggableScope &&
                typeof dragIndex === 'number' &&
                dragModelPath &&
                dragSettings.placeholder !== 'keep' &&
                !dropSettings.deepCopy;

              if (shouldRemoveFromSource) {
                dragDropService.removeFromModel(draggableScope, dragModelPath, dragIndex);
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
