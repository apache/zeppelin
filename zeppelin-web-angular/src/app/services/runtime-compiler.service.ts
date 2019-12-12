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

import {
  Compiler,
  Component,
  Injectable,
  ModuleWithComponentFactories,
  NgModule,
  NgModuleFactory,
  Type
} from '@angular/core';

import { RuntimeDynamicModuleModule } from '@zeppelin/core';
import { NgZService } from './ng-z.service';

export class DynamicTemplate {
  constructor(
    public readonly template: string,
    // tslint:disable-next-line:no-any
    public readonly component: Type<any>,
    // tslint:disable-next-line:no-any
    public readonly moduleFactory?: NgModuleFactory<any>
  ) {}
}

@Injectable({
  providedIn: 'root'
})
export class RuntimeCompilerService {
  // tslint:disable-next-line:no-any
  private compiledModule?: ModuleWithComponentFactories<any>;

  public async createAndCompileTemplate(paragraphId: string, template: string): Promise<DynamicTemplate> {
    const ngZService = this.ngZService;
    const dynamicComponent = Component({ template: template })(
      class DynamicTemplateComponent {
        z = {
          set: (key: string, value, id: string) => ngZService.setContextValue(key, value, id),
          unset: (key: string, id: string) => ngZService.unsetContextValue(key, id),
          run: (id: string) => ngZService.runParagraph(id)
        };

        constructor() {
          ngZService.bindParagraph(paragraphId, this);
          Object.freeze(this.z);
        }
      }
    );
    const dynamicModule = NgModule({
      declarations: [dynamicComponent],
      exports: [dynamicComponent],
      entryComponents: [dynamicComponent],
      imports: [RuntimeDynamicModuleModule]
    })(class DynamicModule {});

    this.compiledModule = await this.compiler.compileModuleAndAllComponentsAsync(dynamicModule);
    return new DynamicTemplate(template, dynamicComponent, this.compiledModule.ngModuleFactory);
  }

  constructor(private compiler: Compiler, private ngZService: NgZService) {}
}
