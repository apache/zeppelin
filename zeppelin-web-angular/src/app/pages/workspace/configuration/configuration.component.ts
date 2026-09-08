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
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ConfigurationService, ReactFeatureService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  configEntries: Array<[string, string]> = [];
  useReactTable = false;
  reactTableFailed = false;

  private destroy$ = new Subject<void>();
  private lastReactTableProps: Record<string, unknown> | null = null;

  constructor(
    private configurationService: ConfigurationService,
    private activatedRoute: ActivatedRoute,
    private reactFeature: ReactFeatureService,
    private cdr: ChangeDetectorRef
  ) {}

  get shouldUseReactTable(): boolean {
    return this.useReactTable && !this.reactTableFailed;
  }

  // Memoized on configEntries, the only input that changes. An object literal in
  // the template would hand ReactMountDirective a new identity on every
  // change-detection pass and make it call handle.update() each time.
  get reactTableProps(): Record<string, unknown> {
    if (this.lastReactTableProps?.entries !== this.configEntries) {
      this.lastReactTableProps = { entries: this.configEntries, onError: this.onReactTableError };
    }
    return this.lastReactTableProps;
  }

  readonly onReactTableError = (error: unknown): void => {
    console.error('React configuration table error', error);
    this.reactTableFailed = true;
    this.cdr.markForCheck();
  };

  ngOnInit() {
    // Subscribed rather than read once: navigating between /configuration and
    // /configuration?reactConfiguration reuses this component, so a snapshot
    // read would keep the flag it saw first.
    this.activatedRoute.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.useReactTable = this.reactFeature.isEnabled('configurationTable', params);
      this.cdr.markForCheck();
    });
    this.getAllConfig();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getAllConfig(): void {
    this.configurationService.getAll().subscribe(data => {
      this.configEntries = [...Object.entries<string>(data)].sort((a, b) => a[0].localeCompare(b[0]));
      this.cdr.markForCheck();
    });
  }
}
