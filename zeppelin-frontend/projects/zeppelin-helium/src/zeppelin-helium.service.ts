import { Injectable, Type } from '@angular/core';
import { Visualization } from '@zeppelin/visualization';
import { COMMON_DEPS } from './common-deps';
import { ZeppelinHeliumModule } from './zeppelin-helium.module';

// tslint:disable-next-line:no-any
const SystemJs = (window as any).System;

// tslint:disable-next-line:no-any
export class ZeppelinHeliumPackage {
  constructor(
    public name: string,
    public id: string,
    // tslint:disable-next-line:no-any
    public module: Type<any>,
    // tslint:disable-next-line:no-any
    public component: Type<any>,
    // tslint:disable-next-line:no-any
    public visualization?: any,
    public icon = 'build'
  ) {
  }
}

export enum HeliumPackageType {
  Visualization
}

// tslint:disable-next-line:no-any
export function createHeliumPackage(config: {
  name: string;
  id: string;
  icon?: string;
  type: HeliumPackageType;
  // tslint:disable-next-line:no-any
  module: Type<any>;
  // tslint:disable-next-line:no-any
  component: Type<any>;
  // tslint:disable-next-line:no-any
  visualization?: any
}) {
  return new ZeppelinHeliumPackage(
    config.name,
    config.id,
    config.module,
    config.component,
    config.visualization,
    config.icon
  );
}

@Injectable({
  providedIn: ZeppelinHeliumModule
})
export class ZeppelinHeliumService {

  depsDefined = false;

  constructor() { }

  defineDeps() {
    if (this.depsDefined) {
      return;
    }
    Object.keys(COMMON_DEPS).forEach(externalKey =>
      // tslint:disable-next-line:no-any
      (window as any).define(externalKey, [], () => COMMON_DEPS[ externalKey ])
    );
    this.depsDefined = true;
  }

  loadPackage(name: string): Promise<ZeppelinHeliumPackage> {
    this.defineDeps();
    return SystemJs.import(`./assets/helium-packages/${name}.umd.js`)
      .then(() => SystemJs.import(name))
      .then(plugin => {
        if (plugin instanceof ZeppelinHeliumPackage) {
          return Promise.resolve(plugin);
        } else {
          throw new TypeError('This module is not a valid helium package');
        }
      });
  }
}
