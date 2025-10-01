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

import { Injectable, OnDestroy } from '@angular/core';
import { combineLatest, fromEvent, BehaviorSubject, Observable, Subscription } from 'rxjs';
import { distinctUntilChanged, map, startWith } from 'rxjs/operators';

export type ThemeMode = 'light' | 'dark' | 'system';

const THEME_STORAGE_KEY = 'zeppelin-theme';
const MONACO_THEMES = {
  light: 'vs',
  dark: 'vs-dark'
} as const;

@Injectable({
  providedIn: 'root'
})
export class ThemeService implements OnDestroy {
  private themeSubject: BehaviorSubject<ThemeMode>;
  private effectiveThemeSubject: BehaviorSubject<'light' | 'dark'>;
  private systemStartedWith: 'light' | 'dark' | null = null;
  private subscriptions = new Subscription();

  public theme$: Observable<ThemeMode>;
  public effectiveTheme$: Observable<'light' | 'dark'>;

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    this.themeSubject.complete();
    this.effectiveThemeSubject.complete();
  }

  constructor() {
    const initialTheme = this.detectInitialTheme();
    this.themeSubject = new BehaviorSubject<ThemeMode>(initialTheme);
    this.theme$ = this.themeSubject.asObservable();

    // Create observable for system theme changes
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const systemTheme$ = fromEvent<MediaQueryListEvent>(mediaQuery, 'change').pipe(
      map(e => (e.matches ? ('dark' as const) : ('light' as const))),
      startWith(mediaQuery.matches ? ('dark' as const) : ('light' as const)),
      distinctUntilChanged()
    );

    // Calculate initial effective theme
    const initialEffectiveTheme = this.resolveEffectiveTheme(initialTheme, mediaQuery.matches);
    this.effectiveThemeSubject = new BehaviorSubject<'light' | 'dark'>(initialEffectiveTheme);
    this.effectiveTheme$ = this.effectiveThemeSubject.asObservable();

    // Reactively update effective theme when either theme or system theme changes
    const subscription = combineLatest([this.theme$, systemTheme$])
      .pipe(
        map(([theme, systemTheme]) => (theme === 'system' ? systemTheme : theme)),
        distinctUntilChanged()
      )
      .subscribe(effectiveTheme => {
        this.effectiveThemeSubject.next(effectiveTheme);
        this.applyTheme(effectiveTheme);
      });

    this.subscriptions.add(subscription);
  }

  private detectInitialTheme(): ThemeMode {
    try {
      const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
      if (savedTheme && this.isValidTheme(savedTheme)) {
        return savedTheme;
      }
      return 'system';
    } catch {
      return 'system';
    }
  }

  private isValidTheme(theme: string): theme is ThemeMode {
    return theme === 'light' || theme === 'dark' || theme === 'system';
  }

  private resolveEffectiveTheme(theme: ThemeMode, isDarkMode: boolean): 'light' | 'dark' {
    return theme === 'system' ? (isDarkMode ? 'dark' : 'light') : theme;
  }

  getCurrentTheme(): ThemeMode {
    return this.themeSubject.value;
  }

  private getEffectiveTheme(): 'light' | 'dark' {
    return this.effectiveThemeSubject.value;
  }

  private setTheme(theme: ThemeMode, save: boolean = true) {
    if (this.themeSubject.value === theme) {
      return;
    }

    this.themeSubject.next(theme);

    if (save) {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    }
  }

  toggleTheme() {
    const currentTheme = this.getCurrentTheme();
    const effectiveTheme = this.getEffectiveTheme();

    if (currentTheme === 'system') {
      this.systemStartedWith = effectiveTheme;
      this.setTheme(effectiveTheme === 'dark' ? 'light' : 'dark');
    } else if (currentTheme === 'dark') {
      if (this.systemStartedWith === 'dark') {
        this.setTheme('system');
        this.systemStartedWith = null;
      } else {
        this.setTheme('light');
      }
    } else {
      if (this.systemStartedWith === 'light') {
        this.setTheme('system');
        this.systemStartedWith = null;
      } else if (this.systemStartedWith === 'dark') {
        this.setTheme('dark');
      } else {
        this.setTheme('system');
      }
    }
  }

  private applyTheme(effectiveTheme: 'light' | 'dark') {
    const html = document.documentElement;
    const body = document.body;

    [html, body].forEach(el => {
      el.classList.toggle('dark', effectiveTheme === 'dark');
      el.classList.toggle('light', effectiveTheme === 'light');
      el.setAttribute('data-theme', effectiveTheme);
    });

    html.style.setProperty('color-scheme', effectiveTheme);

    this.updateMonacoTheme();
  }

  updateMonacoTheme() {
    if (!monaco?.editor) {
      return;
    }

    const effectiveTheme = this.getEffectiveTheme();

    try {
      // Fix editor not applying dark mode on first load when theme is set to "system"
      requestAnimationFrame(() => {
        monaco.editor.setTheme(MONACO_THEMES[effectiveTheme]);
      });
    } catch (error) {
      console.error('Monaco theme setting failed:', error);
    }
  }
}
