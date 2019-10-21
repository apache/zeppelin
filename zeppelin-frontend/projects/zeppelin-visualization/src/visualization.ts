import { ComponentRef } from '@angular/core';
import { Subject } from 'rxjs';

import { GraphConfig } from '@zeppelin/sdk';
import { Transformation } from './transformation';

// tslint:disable-next-line
export abstract class Visualization<T = any> {
  // tslint:disable-next-line
  transformed: any;
  componentRef: ComponentRef<T>;
  configChange$ = new Subject<GraphConfig>();
  constructor(private config: GraphConfig) {}

  abstract getTransformation(): Transformation;
  abstract render(tableData): void;
  abstract refresh(): void;
  abstract destroy(): void;

  configChanged() {
    return this.configChange$.asObservable();
  }

  setConfig(config: GraphConfig) {
    this.config = config;
    this.refresh();
  }

  getConfig() {
    return this.config;
  }
}
