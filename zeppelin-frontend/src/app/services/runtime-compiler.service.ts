import { CommonModule } from '@angular/common';
import {
  Compiler,
  Component,
  Injectable,
  ModuleWithComponentFactories,
  NgModule,
  NgModuleFactory,
  Type
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NgZorroAntdModule } from 'ng-zorro-antd';

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
      imports: [CommonModule, NgZorroAntdModule, FormsModule]
    })(class DynamicModule {});

    this.compiledModule = await this.compiler.compileModuleAndAllComponentsAsync(dynamicModule);
    return new DynamicTemplate(template, dynamicComponent, this.compiledModule.ngModuleFactory);
  }

  constructor(private compiler: Compiler, private ngZService: NgZService) {}
}
