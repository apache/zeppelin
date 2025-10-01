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
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ThemeMode, ThemeService } from '../../services/theme.service';

@Component({
  selector: 'zeppelin-theme-toggle',
  templateUrl: './theme-toggle.component.html',
  styleUrls: ['./theme-toggle.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ThemeToggleComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject();
  currentTheme: ThemeMode = 'light';
  isDarkMode = false;

  constructor(private themeService: ThemeService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.currentTheme = this.themeService.getCurrentTheme();
    this.isDarkMode = this.currentTheme === 'dark';

    this.themeService.theme$.pipe(takeUntil(this.destroy$)).subscribe(theme => {
      if (this.currentTheme !== theme) {
        this.currentTheme = theme;
        this.isDarkMode = theme === 'dark';
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }

  getThemeIcon(): string {
    if (this.currentTheme === 'system') {
      return '🤖';
    }
    return this.currentTheme === 'dark' ? '🌙' : '☀️';
  }
}
