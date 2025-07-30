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

import { HttpClient } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { GraphConfig } from '@zeppelin/sdk';
import { TableData } from '@zeppelin/visualization';
import * as angular from 'angular';
import { TableDataAdapterService } from './table-data-adapter.service';

interface ClassicVisualizationInstance {
  // tslint:disable-next-line:no-any
  instance: any;
  targetEl: HTMLElement;
  scope: angular.IScope;
  appName: string;
  // tslint:disable-next-line:no-any
  injector: any;
}

@Injectable({
  providedIn: 'root'
})
export class ClassicVisualizationService {
  private appCounter = 0;
  private activeInstances = new Map<string, ClassicVisualizationInstance>();

  // Template cache for known HTML templates
  private templateCache = new Map<string, string>();

  constructor(
    private injector: Injector,
    private tableDataAdapter: TableDataAdapterService,
    private http: HttpClient
  ) {}

  // Map of template URLs to asset file paths
  private templateAssetMapping = new Map<string, string>([
    [
      'app/tabledata/advanced-transformation-setting.html',
      'assets/classic-visualization-templates/advanced-transformation-setting.html'
    ]
  ]);

  // Custom $templateRequest implementation that intercepts known template paths
  // tslint:disable-next-line:no-any
  private createCustomTemplateRequest(originalTemplateRequest: any): any {
    return (templateUrl: string, ignorRequestError?: boolean) => {
      // Check if we have a cached template for this URL
      if (this.templateCache.has(templateUrl)) {
        // Return a promise that resolves with the cached template
        return Promise.resolve(this.templateCache.get(templateUrl));
      }

      // Check if this is a known template that should be loaded from assets
      const assetPath = this.templateAssetMapping.get(templateUrl);
      if (assetPath) {
        console.log(`Loading template from asset: ${templateUrl} -> ${assetPath}`);
        // Load from assets and cache the result
        return this.http
          .get(assetPath, { responseType: 'text' })
          .toPromise()
          .then((templateContent: string) => {
            console.log(`Successfully loaded template: ${templateUrl}`, templateContent.length);
            // Cache the loaded template
            this.templateCache.set(templateUrl, templateContent);
            return templateContent;
          })
          .catch(error => {
            console.error(`Failed to load template from ${assetPath}:`, error);
            // Fallback to original $templateRequest
            return originalTemplateRequest(templateUrl, ignorRequestError);
          });
      }

      // For unknown templates, delegate to the original $templateRequest
      return originalTemplateRequest(templateUrl, ignorRequestError);
    };
  }

  private waitForElement(elementId: string, maxRetries = 50, interval = 100): Promise<HTMLElement> {
    return new Promise((resolve, reject) => {
      let retries = 0;

      const checkElement = () => {
        const element = document.getElementById(elementId);
        if (element) {
          resolve(element);
          return;
        }

        retries++;
        if (retries >= maxRetries) {
          reject(new Error(`Element not found after ${maxRetries} retries: ${elementId}`));
          return;
        }

        setTimeout(checkElement, interval);
      };

      checkElement();
    });
  }

  private getTransformationSettingElement(targetElementId: string): HTMLElement | null {
    // Extract the base ID from targetElementId (e.g., "p123_table" -> "123_table")
    const baseId = targetElementId.replace(/^p/, '');
    const trSettingId = `trsetting${baseId}`;
    const element = document.getElementById(trSettingId);
    console.log(`Looking for transformation setting element: ${trSettingId}`, element);
    return element;
  }

  private getVisualizationSettingElement(targetElementId: string): HTMLElement | null {
    // Extract the base ID from targetElementId (e.g., "p123_table" -> "123_table")
    const baseId = targetElementId.replace(/^p/, '');
    const vizSettingId = `vizsetting${baseId}`;
    const element = document.getElementById(vizSettingId);
    console.log(`Looking for visualization setting element: ${vizSettingId}`, element);
    return element;
  }

  // tslint:disable-next-line:no-any
  private waitForTransformationScopeAndApply(transformation: any, timeout: any): void {
    const waitForTransformationScope = () => {
      if (transformation._scope) {
        transformation._scope.$apply();
      } else {
        timeout(waitForTransformationScope, 10);
      }
    };
    timeout(waitForTransformationScope, 0);
  }

  async createClassicVisualization(
    // tslint:disable-next-line:no-any
    visualizationClass: any,
    targetElementId: string,
    config: GraphConfig,
    tableData: TableData,
    // tslint:disable-next-line:no-any
    emitter: (config: any) => void
    // tslint:disable-next-line:no-any
  ): Promise<any> {
    // Wait for DOM element to be available
    const targetElement = await this.waitForElement(targetElementId);

    // Clean up any existing instance for this element
    this.destroyInstance(targetElementId);

    // Create unique app name
    const appName = `classicVizApp_${this.appCounter++}`;

    // Create AngularJS module
    const module = angular.module(appName, []);

    // Add custom drag and drop directives
    this.addDragDropDirectives(module);

    // Create AngularJS app and bootstrap
    const injector = angular.bootstrap(targetElement, [appName]);
    const rootScope = injector.get('$rootScope');
    const compile = injector.get('$compile');
    const originalTemplateRequest = injector.get('$templateRequest');

    // Create custom templateRequest that intercepts known template paths
    const templateRequest = this.createCustomTemplateRequest(originalTemplateRequest);
    const timeout = injector.get('$timeout');

    // Create scope for this visualization
    const scope = rootScope.$new(true);

    // Create angular element wrapper
    const angularElement = angular.element(targetElement);

    // Convert modern TableData to classic format
    const classicTableData = this.tableDataAdapter.createClassicTableDataProxy(tableData);

    // Instantiate the classic visualization
    const vizInstance = new visualizationClass(angularElement, config);

    // Inject AngularJS dependencies that classic visualizations expect
    vizInstance._emitter = emitter;
    vizInstance._compile = compile;
    vizInstance._createNewScope = () => rootScope.$new(true);
    vizInstance._templateRequest = templateRequest;

    // Get or create setting elements
    const transformationSettingEl = this.getTransformationSettingElement(targetElementId);
    const visualizationSettingEl = this.getVisualizationSettingElement(targetElementId);

    // Setup transformation if available
    const transformation = vizInstance.getTransformation();
    if (transformation) {
      transformation._emitter = emitter;
      transformation._templateRequest = templateRequest;
      transformation._compile = compile;
      transformation._createNewScope = () => rootScope.$new(true);

      // Set config and transform data
      transformation.setConfig(config);
      const transformed = transformation.transform(classicTableData);

      // Render transformation setting
      transformation.renderSetting(angular.element(transformationSettingEl));

      // Wait for transformation rendering to complete (including async template loading)
      this.waitForTransformationScopeAndApply(transformation, timeout);

      // Render the visualization
      vizInstance.render(transformed);
    } else {
      // If no transformation, render directly
      vizInstance.render(classicTableData);
    }

    // Render visualization setting
    vizInstance.renderSetting(angular.element(visualizationSettingEl));

    // Activate the visualization
    vizInstance.activate();

    // Store the instance for cleanup later
    this.activeInstances.set(targetElementId, {
      instance: vizInstance,
      targetEl: targetElement,
      scope,
      appName,
      injector
    });

    return vizInstance;
  }

  updateClassicVisualization(targetElementId: string, config: GraphConfig, tableData: TableData): void {
    const instanceData = this.activeInstances.get(targetElementId);
    if (!instanceData) {
      return;
    }

    const { instance } = instanceData;

    try {
      // Convert modern TableData to classic format
      const classicTableData = this.tableDataAdapter.createClassicTableDataProxy(tableData);

      // Get or create setting elements
      const transformationSettingEl = this.getTransformationSettingElement(targetElementId);
      const visualizationSettingEl = this.getVisualizationSettingElement(targetElementId);

      instance.setConfig(config);

      // Update transformation and re-render
      const transformation = instance.getTransformation();
      if (transformation) {
        transformation.setConfig(config);
        const transformed = transformation.transform(classicTableData);

        // Re-render transformation setting
        transformation.renderSetting(angular.element(transformationSettingEl));

        // Wait for transformation rendering to complete (including async template loading)
        const { injector } = instanceData;
        const timeout = injector.get('$timeout');
        this.waitForTransformationScopeAndApply(transformation, timeout);

        instance.render(transformed);
      } else {
        instance.render(classicTableData);
      }

      // Re-render visualization setting
      instance.renderSetting(angular.element(visualizationSettingEl));

      // Refresh if available
      instance.refresh();
    } catch (error) {
      console.error('Error updating classic visualization:', error);
    }
  }

  setClassicVisualizationConfig(targetElementId: string, config: GraphConfig): void {
    const instanceData = this.activeInstances.get(targetElementId);
    if (!instanceData) {
      return;
    }

    const { instance } = instanceData;

    try {
      instance.setConfig(config);

      instance.refresh();
    } catch (error) {
      console.error('Error setting classic visualization config:', error);
    }
  }

  resizeClassicVisualization(targetElementId: string): void {
    const instanceData = this.activeInstances.get(targetElementId);
    if (!instanceData) {
      return;
    }

    const { instance } = instanceData;

    try {
      instance.resize();
    } catch (error) {
      console.error('Error resizing classic visualization:', error);
    }
  }

  destroyInstance(targetElementId: string): void {
    const instanceData = this.activeInstances.get(targetElementId);
    if (!instanceData) {
      return;
    }

    const { instance, targetEl, scope } = instanceData;

    try {
      // Destroy the visualization instance
      instance.destroy();

      // Destroy the scope
      scope.$destroy();

      // Clean up the DOM element
      angular.element(targetEl).empty();

      this.activeInstances.delete(targetElementId);
    } catch (error) {
      console.error('Error destroying classic visualization:', error);
    }
  }

  destroyAllInstances(): void {
    const elementIds = Array.from(this.activeInstances.keys());
    elementIds.forEach(elementId => {
      this.destroyInstance(elementId);
    });
  }

  // tslint:disable-next-line:no-any
  private addDragDropDirectives(module: any): void {
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
