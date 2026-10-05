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

import { NO_ERRORS_SCHEMA, provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DynamicForms, DynamicFormsType, FormValue } from '@zeppelin/sdk';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { describe, expect, it } from 'vitest';

import { NotebookParagraphDynamicFormsComponent } from './dynamic-forms.component';
import template from './dynamic-forms.component.html?raw';

describe('Select form hydration (TestBed)', () => {
  it.each([
    { kind: 'object', value: { id: 'a' } },
    { kind: 'array', value: ['a', 42] }
  ])('displays an independently decoded $kind option', async ({ value }) => {
    await TestBed.configureTestingModule({
      declarations: [NotebookParagraphDynamicFormsComponent],
      imports: [FormsModule, NzSelectModule],
      providers: [provideZoneChangeDetection()],
      schemas: [NO_ERRORS_SCHEMA]
    })
      .overrideComponent(NotebookParagraphDynamicFormsComponent, {
        set: { template, templateUrl: undefined, styles: [], styleUrls: [] }
      })
      .compileComponents();

    const fixture = TestBed.createComponent(NotebookParagraphDynamicFormsComponent);
    const formDefs: DynamicForms = {
      field: {
        name: 'field',
        type: DynamicFormsType.Select,
        hidden: false,
        defaultValue: value,
        options: [{ value, displayName: 'Option A' }]
      }
    };
    const wire = JSON.parse(JSON.stringify({ forms: formDefs, params: { field: value } })) as {
      forms: DynamicForms;
      params: { field: FormValue };
    };
    expect(wire.params.field).not.toBe(wire.forms.field.options![0].value);
    fixture.componentInstance.formDefs = wire.forms;
    fixture.componentInstance.paramDefs = wire.params;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const selected = fixture.nativeElement.querySelector('.ant-select-selection-item') as HTMLElement | null;
    expect(selected).not.toBeNull();
    expect(selected?.textContent).toContain('Option A');
    expect(fixture.componentInstance.paramDefs.field).toEqual(value);
  });
});

describe('Checkbox form interaction (TestBed)', () => {
  it.each([DynamicFormsType.CheckBox, DynamicFormsType.LegacyCheckBox])(
    'submits original %s values and restores selection after a broadcast',
    async type => {
      await TestBed.configureTestingModule({
        declarations: [NotebookParagraphDynamicFormsComponent],
        imports: [FormsModule, NzCheckboxModule],
        providers: [provideZoneChangeDetection()],
        schemas: [NO_ERRORS_SCHEMA]
      })
        .overrideComponent(NotebookParagraphDynamicFormsComponent, {
          set: { template, templateUrl: undefined, styles: [], styleUrls: [] }
        })
        .compileComponents();

      const fixture = TestBed.createComponent(NotebookParagraphDynamicFormsComponent);
      const formDefs: DynamicForms = {
        field: {
          name: 'field',
          type,
          hidden: false,
          defaultValue: [],
          options: [false, 0, '', { id: 'a' }, ['a', 42]].map(value => ({ value }))
        }
      };
      fixture.componentRef.setInput('formDefs', formDefs);
      fixture.componentRef.setInput('paramDefs', { field: [] });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = Array.from(fixture.nativeElement.querySelectorAll('input[type="checkbox"]')) as HTMLInputElement[];
      expect(inputs).toHaveLength(5);
      for (const input of inputs) {
        input.click();
        fixture.detectChanges();
        await fixture.whenStable();
      }
      inputs[1].click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const expectedValues = [false, '', { id: 'a' }, ['a', 42]];
      expect(fixture.componentInstance.paramDefs.field).toEqual(expectedValues);
      const submitted: string[] = [];
      fixture.componentInstance.formChange.subscribe(() => {
        submitted.push(JSON.stringify(fixture.componentInstance.paramDefs));
      });
      fixture.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      expect(submitted).toEqual([JSON.stringify({ field: expectedValues })]);

      const broadcast = JSON.parse(JSON.stringify({ forms: formDefs, params: JSON.parse(submitted[0]) })) as {
        forms: DynamicForms;
        params: { field: FormValue };
      };
      fixture.componentRef.setInput('formDefs', broadcast.forms);
      fixture.componentRef.setInput('paramDefs', broadcast.params);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const restored = Array.from(
        fixture.nativeElement.querySelectorAll('input[type="checkbox"]')
      ) as HTMLInputElement[];
      expect(restored.map(input => input.checked)).toEqual([true, false, true, true, true]);
      expect(fixture.componentInstance.paramDefs.field).toEqual(expectedValues);
      expect(submitted).toHaveLength(1);
    }
  );
});
