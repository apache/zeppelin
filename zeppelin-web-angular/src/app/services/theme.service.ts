import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

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
  private readonly THEME_KEY = THEME_STORAGE_KEY;
  private currentTheme: BehaviorSubject<ThemeMode>;
  public theme$: Observable<ThemeMode>;
  private currentEffectiveTheme: BehaviorSubject<'light' | 'dark'>;
  public effectiveTheme$: Observable<'light' | 'dark'>;
  private mediaQuery?: MediaQueryList;
  private systemThemeListener?: (e: MediaQueryListEvent) => void;

  ngOnDestroy(): void {
    this.removeSystemThemeListener();
    this.currentTheme.complete();
    this.currentEffectiveTheme.complete();
  }

  constructor() {
    const initialTheme = this.detectInitialTheme();
    this.currentTheme = new BehaviorSubject<ThemeMode>(initialTheme);
    this.theme$ = this.currentTheme.asObservable();

    const initialEffectiveTheme = this.resolveEffectiveTheme(initialTheme);
    this.currentEffectiveTheme = new BehaviorSubject<'light' | 'dark'>(initialEffectiveTheme);
    this.effectiveTheme$ = this.currentEffectiveTheme.asObservable();

    this.initSystemThemeDetection();

    this.applyTheme(initialEffectiveTheme, false);
  }

  detectInitialTheme(): ThemeMode {
    try {
      const savedTheme = localStorage.getItem(this.THEME_KEY) as ThemeMode | null;
      if (savedTheme && this.isValidTheme(savedTheme)) {
        return savedTheme;
      }
      return 'system';
    } catch {
      return 'system';
    }
  }

  isValidTheme(theme: string): theme is ThemeMode {
    return theme === 'light' || theme === 'dark' || theme === 'system';
  }

  resolveEffectiveTheme(theme: ThemeMode): 'light' | 'dark' {
    if (theme === 'system') {
      return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light';
    }
    return theme;
  }

  getCurrentTheme(): ThemeMode {
    return this.currentTheme.value;
  }

  getEffectiveTheme(): 'light' | 'dark' {
    return this.currentEffectiveTheme.value;
  }

  isDarkMode(): boolean {
    return this.currentEffectiveTheme.value === 'dark';
  }

  setTheme(theme: ThemeMode, save: boolean = true): void {
    if (this.currentTheme.value === theme) {
      return;
    }

    this.currentTheme.next(theme);
    const effectiveTheme = this.resolveEffectiveTheme(theme);
    this.currentEffectiveTheme.next(effectiveTheme);
    this.applyTheme(effectiveTheme);

    if (save) {
      localStorage.setItem(this.THEME_KEY, theme);
    }

    // Update system theme listener based on new theme
    if (theme === 'system') {
      this.initSystemThemeDetection();
    } else {
      this.removeSystemThemeListener();
    }
  }

  toggleTheme(): void {
    const currentTheme = this.getCurrentTheme();
    if (currentTheme === 'system') {
      this.setTheme('dark');
    } else if (currentTheme === 'dark') {
      this.setTheme('light');
    } else {
      this.setTheme('system');
    }
  }

  applyTheme(effectiveTheme: 'light' | 'dark', updateExternal: boolean = true): void {
    const html = document.documentElement;
    const body = document.body;

    [html, body].forEach(el => {
      el.classList.toggle('dark', effectiveTheme === 'dark');
      el.classList.toggle('light', effectiveTheme === 'light');
      el.setAttribute('data-theme', effectiveTheme);
    });

    html.style.setProperty('color-scheme', effectiveTheme);

    if (updateExternal) {
      this.updateMonacoTheme(effectiveTheme);
    }
  }

  updateMonacoTheme(effectiveTheme: 'light' | 'dark'): void {
    this.applyMonacoTheme(MONACO_THEMES[effectiveTheme]);
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
    // Only set up listener if current theme is 'system'
    if (this.getCurrentTheme() !== 'system') {
      return;
    }

    this.removeSystemThemeListener();

    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    this.systemThemeListener = (e: MediaQueryListEvent) => {
      if (this.getCurrentTheme() === 'system') {
        const newEffectiveTheme: 'light' | 'dark' = e.matches ? 'dark' : 'light';
        this.currentEffectiveTheme.next(newEffectiveTheme);
        this.applyTheme(newEffectiveTheme);
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
    this.updateMonacoTheme(this.getEffectiveTheme());
  }
}
