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

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExternalLinkDirective } from './external-link.directive';

@Component({
  standalone: false,
  template: `
    <a [href]="url">link</a>
  `
})
class HostComponent {
  url = '';
}

/**
 * The directive declares `@Input() href`, so a `[href]` binding goes to the directive
 * instead of the anchor's DOM property and skips Angular's built-in URL sanitization.
 * These tests drive the directive through a real `[href]` binding to cover that path.
 */
describe('ExternalLinkDirective', () => {
  let fixture: ComponentFixture<HostComponent>;

  const anchor = (): HTMLAnchorElement => fixture.nativeElement.querySelector('a');

  const render = (url: string) => {
    fixture.componentInstance.url = url;
    // TestBed is zoneless by default, so flag the host dirty before re-rendering.
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };

  beforeEach(() => {
    // Angular logs a warning whenever it sanitizes a URL; keep the test output clean.
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      declarations: [HostComponent, ExternalLinkDirective]
    });

    fixture = TestBed.createComponent(HostComponent);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('neutralizes javascript: URLs', () => {
    render('javascript:alert(document.domain)');

    expect(anchor().getAttribute('href')).toMatch(/^unsafe:/);
    expect(anchor().protocol).not.toBe('javascript:');
  });

  it('neutralizes javascript: URLs set after an initial safe URL', () => {
    render('https://zeppelin.apache.org/');
    render('javascript:alert(document.domain)');

    expect(anchor().getAttribute('href')).toMatch(/^unsafe:/);
  });

  it('keeps safe external URLs and opens them in a new tab', () => {
    render('https://zeppelin.apache.org/');

    expect(anchor().href).toBe('https://zeppelin.apache.org/');
    expect(anchor().getAttribute('rel')).toBe('noopener noreferrer');
    expect(anchor().getAttribute('target')).toBe('_blank');
  });

  it('keeps same-origin URLs without rel/target', () => {
    render(`${location.origin}/#/notebook/abc`);

    expect(anchor().href).toBe(`${location.origin}/#/notebook/abc`);
    expect(anchor().hasAttribute('rel')).toBe(false);
    expect(anchor().hasAttribute('target')).toBe(false);
  });
});
