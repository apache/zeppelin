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

import { GraphConfig, ParagraphIResultsMsgItem } from '@zeppelin/sdk';
import * as angular from 'angular';
import * as JQuery from 'jquery';

export type HeliumType = 'VISUALIZATION';

enum HeliumPackageType {
  Visualization
}

interface HeliumPackage {
  name: string;
  artifact: string;
  description: string;
  icon: string;
  type: HeliumPackageType;
  license: string;
  published: string;
}

export interface HeliumPackageSearchResult {
  registry: string;
  pkg: HeliumPackage;
  enabled: boolean;
}

export interface HeliumBundle {
  id: string;
  name: string;
  icon: string;
  type: HeliumType;
  class: unknown;
}

export interface HeliumVisualizationBundle extends HeliumBundle {
  type: 'VISUALIZATION';
  class: HeliumClassicVisualizationConstructor;
}

/**
 * Interface representing the legacy `Visualization` class from the original `zeppelin-web` implementation.
 *
 * Since Helium packages are dynamically loaded and evaluated at runtime, classes extended by these packages
 * do not inherit from the same parent `Visualization` class instance. This interface provides a contract
 * that mimics the structure and behavior of the original `Visualization` class to ensure type safety
 * and compatibility across different visualization implementations.
 *
 * This interface is used for classic AngularJS-based visualizations that rely on jQuery and Angular 1.x APIs.
 */
export interface HeliumClassicVisualization {
  _compile: angular.ICompileService;
  _createNewScope(): angular.IScope;
  _emitter(config: unknown): void;
  _templateRequest(tpl: string, ignoreRequestError?: boolean): Promise<string> | angular.IPromise<string>;
  setConfig(config: unknown): void;
  getTransformation(): HeliumClassicTransformation;
  render(transformedTableData: unknown): void;
  refresh(): void;
  destroy(): void;
  renderSetting(elem: JQuery<HTMLElement>): void;
  activate(): void;
  deactivate(): void;
  resize(): void;
}

/**
 * Constructor type for creating instances of classic Helium visualizations.
 *
 * @param targetEl - jQuery-wrapped HTML element where the visualization will be rendered
 * @param config - Graph configuration object containing visualization settings
 * @returns A new instance of HeliumClassicVisualization
 */
export interface HeliumClassicVisualizationConstructor {
  prototype: {};
  new (targetEl: JQuery<HTMLElement>, config: GraphConfig): HeliumClassicVisualization;
}

/**
 * Interface for the transformation component of classic Helium visualizations.
 *
 * This interface represents the data transformation layer that processes raw table data
 * before it's rendered by the visualization. It includes methods for data transformation,
 * configuration management, and settings rendering.
 */
export interface HeliumClassicTransformation {
  _compile: angular.ICompileService;
  _scope?: angular.IScope;
  _createNewScope(): angular.IScope;
  _emitter(config: unknown): void;
  _templateRequest(tpl: string, ignoreRequestError?: boolean): Promise<string> | angular.IPromise<string>;
  setConfig(config: unknown): void;
  transform(tableData: HeliumClassicTableData): unknown;
  renderSetting(elem: JQuery<HTMLElement>): void;
}

export interface HeliumClassicTableData {
  columns: Array<{ name: string; index: number; aggr: 'sum' }>;
  rows: string[][];
  comment: string;
  loadParagraphResult(paragraphResult: ParagraphIResultsMsgItem): void;
  refresh(): void;
}
