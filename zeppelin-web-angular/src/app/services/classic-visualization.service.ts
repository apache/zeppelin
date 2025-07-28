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

import { Injectable, Injector } from '@angular/core';
import { GraphConfig } from '@zeppelin/sdk';
import { TableData } from '@zeppelin/visualization';
import * as angular from 'angular';
import { TableDataAdapterService } from './table-data-adapter.service';

// tslint:disable-next-line:no-any
declare var window: any;

interface ClassicVisualizationInstance {
  // tslint:disable-next-line:no-any
  instance: any;
  targetEl: HTMLElement;
  scope: angular.IScope;
  appName: string;
}

@Injectable({
  providedIn: 'root'
})
export class ClassicVisualizationService {
  private appCounter = 0;
  private activeInstances = new Map<string, ClassicVisualizationInstance>();

  constructor(private injector: Injector, private tableDataAdapter: TableDataAdapterService) {}

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

  createClassicVisualization(
    // tslint:disable-next-line:no-any
    visualizationClass: any,
    targetElementId: string,
    config: GraphConfig,
    tableData: TableData,
    // tslint:disable-next-line:no-any
    emitter: (config: any) => void
    // tslint:disable-next-line:no-any
  ): Promise<any> {
    return new Promise((resolve, reject) => {
      // Wait for DOM element to be available
      this.waitForElement(targetElementId)
        .then(targetElement => {
          try {
            // Clean up any existing instance for this element
            this.destroyInstance(targetElementId);

            // Create unique app name
            const appName = `classicVizApp_${this.appCounter++}`;

            // Create AngularJS module
            const module = angular.module(appName, []);

            // Create AngularJS app and bootstrap
            const injector = angular.bootstrap(targetElement, [appName]);
            const rootScope = injector.get('$rootScope');
            const compile = injector.get('$compile');
            const templateRequest = injector.get('$templateRequest');
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

              // Render the visualization
              vizInstance.render(transformed);
            } else {
              // If no transformation, render directly
              vizInstance.render(classicTableData);
            }

            // Activate the visualization
            if (typeof vizInstance.activate === 'function') {
              vizInstance.activate();
            }

            // Store the instance for cleanup later
            this.activeInstances.set(targetElementId, {
              instance: vizInstance,
              targetEl: targetElement,
              scope,
              appName
            });

            resolve(vizInstance);
          } catch (error) {
            reject(error);
          }
        })
        .catch(error => {
          reject(error);
        });
    });
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

      // Update config if available
      if (typeof instance.setConfig === 'function') {
        instance.setConfig(config);
      }

      // Update transformation and re-render
      const transformation = instance.getTransformation();
      if (transformation) {
        transformation.setConfig(config);
        const transformed = transformation.transform(classicTableData);
        instance.render(transformed);
      } else {
        instance.render(classicTableData);
      }

      // Refresh if available
      if (typeof instance.refresh === 'function') {
        instance.refresh();
      }
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
      if (typeof instance.setConfig === 'function') {
        instance.setConfig(config);
      }

      if (typeof instance.refresh === 'function') {
        instance.refresh();
      }
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
      if (typeof instance.resize === 'function') {
        instance.resize();
      }
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
      if (typeof instance.destroy === 'function') {
        instance.destroy();
      }

      // Destroy the scope
      if (scope && typeof scope.$destroy === 'function') {
        scope.$destroy();
      }

      // Clean up the DOM element
      if (targetEl) {
        angular.element(targetEl).empty();
      }

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
}
