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

import { CdkPortalOutlet, ComponentPortal, ComponentType, PortalInjector } from '@angular/cdk/portal';
import { ComponentFactoryResolver, InjectionToken, ViewContainerRef } from '@angular/core';

import { Visualization } from './visualization';

export const VISUALIZATION = new InjectionToken<Visualization>('Visualization');

export class VisualizationComponentPortal<T extends Visualization, C> {
  constructor(
    private visualization: T,
    private component: ComponentType<C>,
    private portalOutlet: CdkPortalOutlet,
    private viewContainerRef: ViewContainerRef,
    private componentFactoryResolver?: ComponentFactoryResolver
  ) {}

  createInjector() {
    const userInjector = this.viewContainerRef && this.viewContainerRef.injector;
    // tslint:disable-next-line
    const injectionTokens = new WeakMap<any, any>([[VISUALIZATION, this.visualization]]);
    return new PortalInjector(userInjector, injectionTokens);
  }

  getComponentPortal() {
    const injector = this.createInjector();
    return new ComponentPortal(this.component, null, injector, this.componentFactoryResolver);
  }

  attachComponentPortal() {
    const componentRef = this.portalOutlet.attachComponentPortal(this.getComponentPortal());
    componentRef.changeDetectorRef.markForCheck();
    return componentRef;
  }
}
