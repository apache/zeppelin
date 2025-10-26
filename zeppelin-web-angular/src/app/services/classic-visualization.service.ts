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
import {
  HeliumClassicTransformation,
  HeliumClassicVisualization,
  HeliumClassicVisualizationConstructor
} from '@zeppelin/interfaces';
import { GraphConfig, ParagraphConfigResult } from '@zeppelin/sdk';
import { TableData } from '@zeppelin/visualization';
import * as angular from 'angular';
import { cloneDeep } from 'lodash';

import { AngularDragDropService } from './angular-drag-drop.service';
import { BootstrapCompatibilityService } from './bootstrap-compatibility.service';
import { TableDataAdapterService } from './table-data-adapter.service';

interface ClassicVisualizationInstanceInfo {
  instance: HeliumClassicVisualization;
  targetEl: HTMLElement;
  scope: angular.IScope;
  appName: string;
  injector: angular.auto.IInjectorService;
}

@Injectable({
  providedIn: 'root'
})
export class ClassicVisualizationService {
  private appCounter = 0;
  private activeInstanceInfos = new Map<string, ClassicVisualizationInstanceInfo>();

  // Template cache for known HTML templates
  private templateCache = new Map<string, string>();

  constructor(
    private injector: Injector,
    private tableDataAdapter: TableDataAdapterService,
    private http: HttpClient,
    private angularDragDropService: AngularDragDropService,
    private bootstrapCompatibilityService: BootstrapCompatibilityService
  ) {}

  // Map of template URLs to asset file paths
  private templateAssetMapping = new Map<string, string>([
    [
      'app/tabledata/advanced-transformation-setting.html',
      'assets/classic-visualization-templates/advanced-transformation-setting.html'
    ],
    [
      'app/tabledata/columnselector_settings.html',
      'assets/classic-visualization-templates/columnselector_settings.html'
    ],
    ['app/tabledata/network_settings.html', 'assets/classic-visualization-templates/network_settings.html'],
    ['app/tabledata/pivot_settings.html', 'assets/classic-visualization-templates/pivot_settings.html']
  ]);

  // Custom $templateRequest implementation that intercepts known template paths
  private createCustomTemplateRequest(
    originalTemplateRequest: angular.ITemplateRequestService
  ): (tpl: string, ignorRequestError?: boolean) => Promise<string> | angular.IPromise<string> {
    return (templateUrl: string, ignorRequestError?: boolean) => {
      // Check if we have a cached template for this URL
      if (this.templateCache.has(templateUrl)) {
        // Return a promise that resolves with the cached template
        return Promise.resolve(this.templateCache.get(templateUrl) ?? '');
      }

      // Check if this is a known template that should be loaded from assets
      const assetPath = this.templateAssetMapping.get(templateUrl);
      if (assetPath) {
        // Load from assets and cache the result
        return this.http
          .get(assetPath, { responseType: 'text' })
          .toPromise()
          .then((templateContent: string) => {
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
    return element;
  }

  private getVisualizationSettingElement(targetElementId: string): HTMLElement | null {
    // Extract the base ID from targetElementId (e.g., "p123_table" -> "123_table")
    const baseId = targetElementId.replace(/^p/, '');
    const vizSettingId = `vizsetting${baseId}`;
    const element = document.getElementById(vizSettingId);
    return element;
  }

  private waitForTransformationScopeAndApply(
    transformation: HeliumClassicTransformation,
    timeout: angular.ITimeoutService
  ): void {
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
    visConstructor: HeliumClassicVisualizationConstructor,
    targetElementId: string,
    config: GraphConfig,
    tableData: TableData,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    emitter: (config: any) => void
  ): Promise<HeliumClassicVisualization> {
    // Inject Bootstrap compatibility styles before creating visualization
    this.bootstrapCompatibilityService.injectBootstrapStyles();

    // Wait for DOM element to be available
    const targetElement = await this.waitForElement(targetElementId);

    // Clean up any existing instance for this element
    this.destroyInstance(targetElementId);

    const { injector, appName } = this.getOrCreateInjector(targetElement);

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
    const configForMode = this.getClassicVizConfig(config);
    const vizInstance = new visConstructor(angularElement, configForMode);

    // Inject AngularJS dependencies that classic visualizations expect
    vizInstance._emitter = emitter;
    vizInstance._compile = compile;
    vizInstance._createNewScope = () => rootScope.$new(true);
    vizInstance._templateRequest = templateRequest;

    // Get or create setting elements
    const transformationSettingEl = this.getTransformationSettingElement(targetElementId);
    const visualizationSettingEl = this.getVisualizationSettingElement(targetElementId);
    if (!transformationSettingEl || !visualizationSettingEl) {
      throw new Error('Failed to find setting elements for classic visualization');
    }

    // Setup transformation if available
    const transformation = vizInstance.getTransformation();
    if (transformation) {
      transformation._emitter = emitter;
      transformation._templateRequest = templateRequest;
      transformation._compile = compile;
      transformation._createNewScope = () => rootScope.$new(true);

      // Set config and transform data
      transformation.setConfig(configForMode);
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
    this.activeInstanceInfos.set(targetElementId, {
      instance: vizInstance,
      targetEl: targetElement,
      scope,
      appName,
      injector
    });

    return vizInstance;
  }

  private getOrCreateInjector(targetElement: HTMLElement): {
    injector: angular.auto.IInjectorService;
    appName: string;
  } {
    // Check if element is already bootstrapped
    const existingInjector = angular.element(targetElement).injector();

    if (existingInjector) {
      // Reuse existing bootstrap
      return {
        injector: existingInjector,
        appName: 'existingApp' // We don't need the actual name for reuse
      };
    } else {
      // Create unique app name
      const appName = `classicVizApp_${this.appCounter++}`;

      // Create AngularJS module
      const module = angular.module(appName, []);

      // Add custom drag and drop directives
      this.angularDragDropService.addDragDropDirectives(module);

      // Create AngularJS app and bootstrap
      const injector = angular.bootstrap(targetElement, [appName]);

      return {
        injector,
        appName
      };
    }
  }

  updateClassicVisualization(targetElementId: string, config: GraphConfig, tableData: TableData): void {
    const instanceInfo = this.activeInstanceInfos.get(targetElementId);
    if (!instanceInfo) {
      return;
    }

    const { instance } = instanceInfo;

    const configForMode = this.getClassicVizConfig(config);

    try {
      // Convert modern TableData to classic format
      const classicTableData = this.tableDataAdapter.createClassicTableDataProxy(tableData);

      // Get or create setting elements
      const transformationSettingEl = this.getTransformationSettingElement(targetElementId);
      const visualizationSettingEl = this.getVisualizationSettingElement(targetElementId);
      if (!transformationSettingEl || !visualizationSettingEl) {
        throw new Error('Failed to find setting elements for classic visualization');
      }

      instance.setConfig(configForMode);

      // Update transformation and re-render
      const transformation = instance.getTransformation();
      if (transformation) {
        transformation.setConfig(configForMode);
        const transformed = transformation.transform(classicTableData);

        // Re-render transformation setting
        transformation.renderSetting(angular.element(transformationSettingEl));

        // Wait for transformation rendering to complete (including async template loading)
        const { injector } = instanceInfo;
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
    const instanceInfo = this.activeInstanceInfos.get(targetElementId);
    if (!instanceInfo) {
      return;
    }

    const { instance } = instanceInfo;

    try {
      instance.setConfig(config);

      instance.refresh();
    } catch (error) {
      console.error('Error setting classic visualization config:', error);
    }
  }

  resizeClassicVisualization(targetElementId: string): void {
    const instanceInfo = this.activeInstanceInfos.get(targetElementId);
    if (!instanceInfo) {
      return;
    }

    const { instance } = instanceInfo;

    try {
      instance.resize();
    } catch (error) {
      console.error('Error resizing classic visualization:', error);
    }
  }

  destroyInstance(targetElementId: string, forceCleanBootstrap = false): void {
    const instanceInfo = this.activeInstanceInfos.get(targetElementId);
    if (!instanceInfo) {
      return;
    }

    const { instance, targetEl, scope } = instanceInfo;

    try {
      // Destroy the visualization instance
      instance.destroy();

      // Destroy the scope
      scope.$destroy();

      // Clean up the DOM content but preserve the element
      angular.element(targetEl).empty();

      // If forceCleanBootstrap is true, completely remove AngularJS data
      if (forceCleanBootstrap) {
        // Remove all AngularJS data from the element
        angular.element(targetEl).removeData();
        // Remove all classes added by AngularJS
        angular.element(targetEl).removeClass('ng-scope');
      }

      this.activeInstanceInfos.delete(targetElementId);
    } catch (error) {
      console.error('Error destroying classic visualization:', error);
    }
  }

  destroyAllInstances(forceCleanBootstrap = false): void {
    const elementIds = Array.from(this.activeInstanceInfos.keys());
    elementIds.forEach(elementId => {
      this.destroyInstance(elementId, forceCleanBootstrap);
    });
  }

  private getClassicVizConfig(graph: GraphConfig) {
    const mode = graph.mode;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const configForMode: any = graph?.setting?.[mode as keyof ParagraphConfigResult['graph']['setting']]
      ? cloneDeep(graph.setting[mode as keyof ParagraphConfigResult['graph']['setting']])
      : {};

    // copy common setting
    configForMode.common = cloneDeep(graph.commonSetting) || {};

    // copy pivot setting
    if (graph.keys) {
      configForMode.common.pivot = {
        keys: cloneDeep(graph.keys),
        groups: cloneDeep(graph.groups),
        values: cloneDeep(graph.values)
      };
    }

    return configForMode;
  }
}
