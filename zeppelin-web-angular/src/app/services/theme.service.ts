import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ThemeMode = 'light' | 'dark';

const THEME_STORAGE_KEY = 'zeppelin-theme';
const MONACO_THEMES = {
  light: 'vs',
  dark: 'vs-dark'
} as const;

@Injectable({
  providedIn: 'root'
})
export class ThemeService implements OnDestroy {
  private readonly THEME_KEY = THEME_STORAGE_KEY;
  private currentTheme: BehaviorSubject<ThemeMode>;
  public theme$: Observable<ThemeMode>;
  private mediaQuery?: MediaQueryList;
  private systemThemeListener?: (e: MediaQueryListEvent) => void;

  ngOnDestroy(): void {
    this.removeSystemThemeListener();
    this.currentTheme.complete();
  }

  constructor() {
    const initialTheme = this.detectInitialTheme();
    this.currentTheme = new BehaviorSubject<ThemeMode>(initialTheme);
    this.theme$ = this.currentTheme.asObservable();
    this.applyTheme(initialTheme, false);
    this.initSystemThemeDetection();
  }

  detectInitialTheme(): ThemeMode {
    try {
      const savedTheme = localStorage.getItem(this.THEME_KEY) as ThemeMode | null;
      if (savedTheme && this.isValidTheme(savedTheme)) {
        return savedTheme;
      }
      return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }

  isValidTheme(theme: string): theme is ThemeMode {
    return theme === 'light' || theme === 'dark';
  }

  getCurrentTheme(): ThemeMode {
    return this.currentTheme.value;
  }

  isDarkMode(): boolean {
    return this.currentTheme.value === 'dark';
  }

  setTheme(theme: ThemeMode, save: boolean = true): void {
    if (this.currentTheme.value === theme) {
      return;
    }

    this.currentTheme.next(theme);
    this.applyTheme(theme);

    if (save) {
      localStorage.setItem(this.THEME_KEY, theme);
      // When user manually sets theme, stop following system theme
      this.removeSystemThemeListener();
    }
  }

  toggleTheme(): void {
    this.setTheme(this.isDarkMode() ? 'light' : 'dark');
  }

  applyTheme(theme: ThemeMode, updateExternal: boolean = true): void {
    const html = document.documentElement;
    const body = document.body;

    [html, body].forEach(el => {
      el.classList.toggle('dark', theme === 'dark');
      el.classList.toggle('light', theme === 'light');
      el.setAttribute('data-theme', theme);
    });

    html.style.colorScheme = theme;

    if (updateExternal) {
      this.updateMonacoTheme(theme);
    }
  }

  updateMonacoTheme(theme: ThemeMode): void {
    this.applyMonacoTheme(MONACO_THEMES[theme]);
  }

  applyMonacoTheme(targetTheme: string): boolean {
    if (!monaco?.editor) {
      return false;
    }

    try {
      monaco.editor.setTheme(targetTheme);
      return true;
    } catch {
      return false;
    }
  }

  onThemeChange(): Observable<ThemeMode> {
    return this.theme$;
  }

  initSystemThemeDetection(): void {
    // Only follow system theme if no saved preference exists
    if (localStorage.getItem(this.THEME_KEY)) {
      return;
    }

    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    this.systemThemeListener = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem(this.THEME_KEY)) {
        const newTheme: ThemeMode = e.matches ? 'dark' : 'light';
        this.setTheme(newTheme, false);
      }
    };

    this.mediaQuery.addEventListener('change', this.systemThemeListener);
  }

  removeSystemThemeListener(): void {
    if (this.mediaQuery && this.systemThemeListener) {
      this.mediaQuery.removeEventListener('change', this.systemThemeListener);
      this.systemThemeListener = undefined;
    }
  }

  applyMonacoThemeManually(): void {
    this.updateMonacoTheme(this.getCurrentTheme());
  }
}
