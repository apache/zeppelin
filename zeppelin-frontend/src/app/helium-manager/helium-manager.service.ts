import { Compiler, Injectable, Injector, NgModuleFactory, OnDestroy, Type } from '@angular/core';
import { ZeppelinHeliumPackage, ZeppelinHeliumService } from '@zeppelin/helium';
import { of, BehaviorSubject } from 'rxjs';
import { HeliumManagerModule } from './helium-manager.module';

export interface CompiledPackage {
  // tslint:disable-next-line:no-any
  moduleFactory: NgModuleFactory<any>;
  // tslint:disable-next-line:no-any
  component: Type<any>;
  injector?: Injector;
  name: string;
  _raw: ZeppelinHeliumPackage;
}

@Injectable({
  providedIn: HeliumManagerModule
})
export class HeliumManagerService implements OnDestroy {
  private packages$ = new BehaviorSubject<CompiledPackage[]>([]);

  constructor(private zeppelinHeliumService: ZeppelinHeliumService, private compiler: Compiler) {}

  initPackages() {
    this.getEnabledPackages().subscribe(packages => {
      packages.forEach(name => {
        this.zeppelinHeliumService.loadPackage(name).then(heliumPackage => {
          const loaded = this.packages$.value;
          if (!loaded.find(p => p.name === heliumPackage.name)) {
            this.compilePackage(heliumPackage);
          }
        });
      });
    });
  }

  getEnabledPackages() {
    // return of(['helium-vis-example']);
    return of([]);
  }

  packagesLoadChange() {
    return this.packages$.asObservable();
  }

  compilePackage(pack: ZeppelinHeliumPackage) {
    this.compiler.compileModuleAsync(pack.module).then(moduleFactory => {
      this.packages$.next([
        ...this.packages$.value,
        {
          moduleFactory,
          name: pack.name,
          component: pack.component,
          _raw: pack
        }
      ]);
    });
  }

  ngOnDestroy(): void {
    this.packages$.complete();
  }
}
