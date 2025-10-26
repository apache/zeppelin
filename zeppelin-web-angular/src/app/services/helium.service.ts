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
import { Injectable } from '@angular/core';
import { HeliumBundle, HeliumPackageSearchResult, HeliumVisualizationBundle } from '@zeppelin/interfaces/helium';
import { BaseRest } from '@zeppelin/services/base-rest';
import { BaseUrlService } from '@zeppelin/services/base-url.service';
import { forkJoin, of, BehaviorSubject, Observable } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class HeliumService extends BaseRest {
  private visualizationBundles$ = new BehaviorSubject<HeliumVisualizationBundle[]>([]);

  constructor(
    private http: HttpClient,
    baseUrlService: BaseUrlService
  ) {
    super(baseUrlService);
  }

  visualizationBundles() {
    return this.visualizationBundles$.asObservable();
  }

  getSpellByMagic(magic: string): string | null {
    return null;
  }

  private getAllEnabledPackages() {
    return this.http.get<HeliumPackageSearchResult[]>(this.restUrl`/helium/enabledPackage`);
  }

  private getSingleBundle(pkgName: string) {
    return this.http
      .get(this.restUrl`/helium/bundle/load/${pkgName}`, {
        responseType: 'text'
      })
      .pipe(
        map(bundle => {
          if (typeof bundle === 'string' && bundle.substring(0, 'ERROR:'.length) === 'ERROR:') {
            console.error(`Failed to get bundle: ${pkgName}`, bundle);
          }
          return bundle;
        }),
        catchError(error => {
          console.error(`Failed to get single bundle: ${pkgName}`, error);
          return of('');
        })
      );
  }

  private getBundlesParallel(): Observable<string[]> {
    return this.getAllEnabledPackages().pipe(
      switchMap(packages => {
        if (!packages || packages.length === 0) {
          return of([]);
        }

        const bundleRequests = packages.map(helium => this.getSingleBundle(helium.pkg.name));

        return forkJoin(bundleRequests);
      }),
      map(bundles =>
        bundles.reduce((acc, bundle) => {
          if (bundle === '') {
            return acc;
          }
          acc.push(bundle);
          return acc;
        }, [] as string[])
      )
    );
  }

  initPackages() {
    const bundles$ = this.getBundlesParallel();
    bundles$.subscribe(availableBundles => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any)._heliumBundles = [] as HeliumBundle[];
      availableBundles.forEach(bundle => {
        // eslint-disable-next-line no-eval
        eval(bundle);
      });

      const visualizationBundles = [] as HeliumVisualizationBundle[];
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ((window as any)._heliumBundles as HeliumBundle[]).forEach(bundle => {
        switch (bundle.type) {
          case 'VISUALIZATION':
            visualizationBundles.push(bundle as HeliumVisualizationBundle);
        }
      });
      this.visualizationBundles$.next(visualizationBundles);

      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        delete (window as any)._heliumBundles;
      } catch (e) {
        console.error('Failed to delete window.heliumBundles', e);
      }
    });
  }
}
