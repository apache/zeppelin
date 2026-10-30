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

import { NgComponentOutlet } from '@angular/common';
import { Component, provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expect, it } from 'vitest';
import { NgZService } from './ng-z.service';
import { DynamicTemplate, RuntimeCompilerService } from './runtime-compiler.service';

@Component({
  imports: [NgComponentOutlet],
  template:
    '<ng-container *ngComponentOutlet="dynamic?.component; ngModuleFactory: dynamic?.moduleFactory"></ng-container>'
})
class OutputHost {
  dynamic: DynamicTemplate | null = null;
}

it('renders retained values, supports native Angular input, and does not resurrect removals on output recreation', async () => {
  TestBed.configureTestingModule({ imports: [OutputHost], providers: [provideZoneChangeDetection()] });
  const service = TestBed.inject(NgZService);
  service.registerParagraphBindings('note', 'A');
  const binding = { noteId: 'note', paragraphId: null, interpreterGroupId: 'spark-group' };
  service.setContextValue('controlValue', 'hello', 'A', false, binding);
  const compiler = TestBed.inject(RuntimeCompilerService);
  const dynamic = await compiler.createAndCompileTemplate(
    'A',
    "<output>{{controlValue}}</output><button (click)=\"z.set('controlValue', 'edited', 'A')\">Edit</button>"
  );
  const fixture = TestBed.createComponent(OutputHost);
  fixture.componentInstance.dynamic = dynamic;
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  expect(element.querySelector('output')?.textContent).toBe('hello');
  element.querySelector('button')!.click();
  fixture.detectChanges();
  expect(element.querySelector('output')?.textContent).toBe('edited');
  fixture.componentInstance.dynamic = null;
  fixture.detectChanges();
  service.unsetContextValue('controlValue', 'A', false, binding);
  fixture.componentInstance.dynamic = dynamic;
  fixture.detectChanges();
  expect(element.querySelector('output')?.textContent).toBe('');
});

it('uses current binding state when a model is removed while template compilation is pending', async () => {
  TestBed.configureTestingModule({ imports: [OutputHost], providers: [provideZoneChangeDetection()] });
  const service = TestBed.inject(NgZService);
  service.registerParagraphBindings('note', 'A');
  const binding = { noteId: 'note', paragraphId: null, interpreterGroupId: 'spark' };
  service.setContextValue('controlValue', 'removed-during-compilation', 'A', false, binding);
  const pending = TestBed.inject(RuntimeCompilerService).createAndCompileTemplate(
    'A',
    '<output>{{controlValue}}</output>'
  );
  service.unsetContextValue('controlValue', 'A', false, binding);

  const fixture = TestBed.createComponent(OutputHost);
  fixture.componentInstance.dynamic = await pending;
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  expect(element.querySelector('output')?.textContent).toBe('');
  service.setContextValue('controlValue', 'rebound', 'A', false, binding);
  fixture.detectChanges();
  expect(element.querySelector('output')?.textContent).toBe('rebound');
});

it('reapplies repeated server values after a native template-local edit without changing sibling outputs', async () => {
  TestBed.configureTestingModule({ imports: [OutputHost], providers: [provideZoneChangeDetection()] });
  const service = TestBed.inject(NgZService);
  service.registerParagraphBindings('note', 'A');
  const binding = { noteId: 'note', paragraphId: null, interpreterGroupId: 'spark' };
  service.setContextValue('controlValue', 'server', 'A', false, binding);
  const dynamic = await TestBed.inject(RuntimeCompilerService).createAndCompileTemplate(
    'A',
    '<output>{{controlValue}}</output><button (click)="controlValue = \'local\'">Local edit</button>'
  );
  const first = TestBed.createComponent(OutputHost);
  const second = TestBed.createComponent(OutputHost);
  first.componentInstance.dynamic = dynamic;
  second.componentInstance.dynamic = dynamic;
  first.detectChanges();
  second.detectChanges();
  const firstElement = first.nativeElement as HTMLElement;
  const secondElement = second.nativeElement as HTMLElement;
  expect(firstElement.querySelector('output')?.textContent).toBe('server');
  expect(secondElement.querySelector('output')?.textContent).toBe('server');

  firstElement.querySelector('button')!.click();
  first.detectChanges();
  second.detectChanges();
  expect(firstElement.querySelector('output')?.textContent).toBe('local');
  expect(secondElement.querySelector('output')?.textContent).toBe('server');

  service.setContextValue('controlValue', 'server', 'A', false, binding);
  first.detectChanges();
  second.detectChanges();
  expect(firstElement.querySelector('output')?.textContent).toBe('server');
  expect(secondElement.querySelector('output')?.textContent).toBe('server');
});
