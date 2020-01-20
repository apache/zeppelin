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
  ChangeDetectionStrategy,
  Compiler,
  Component,
  Injectable,
  NgModule,
  NgModuleFactory,
  NO_ERRORS_SCHEMA,
  Type
} from '@angular/core';

import { CompileDirectiveMetadata, HtmlParser, TemplateParser } from '@angular/compiler';
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

export class DynamicTemplateError {
  constructor(public message: string) {}
}

@Injectable({
  providedIn: 'root'
})
export class RuntimeCompilerService {
  public async createAndCompileTemplate(paragraphId: string, template: string): Promise<DynamicTemplate> {
    const ngZService = this.ngZService;
    const dynamicComponent = Component({ template: template, selector: `dynamic-${paragraphId}` })(
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

    try {
      this.compiler.clearCache();
      const compiledModule = await this.compiler.compileModuleAndAllComponentsAsync(dynamicModule);
      return new DynamicTemplate(template, dynamicComponent, compiledModule.ngModuleFactory);
    } catch (e) {
      throw new DynamicTemplateError(`${e}`);
    }
  }

  constructor(private compiler: Compiler, private ngZService: NgZService) {}
}
