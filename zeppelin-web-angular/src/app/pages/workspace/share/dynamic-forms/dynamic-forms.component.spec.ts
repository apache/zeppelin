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

import { describe, expect, it, vi } from 'vitest';

import { DynamicFormsType } from '@zeppelin/sdk';

import { NotebookParagraphDynamicFormsComponent } from './dynamic-forms.component';

describe('dynamic form wire values', () => {
  it.each([0, false, '', null])('preserves an existing parameter %j', value => {
    const component = new NotebookParagraphDynamicFormsComponent();
    component.formDefs = {
      field: { name: 'field', type: DynamicFormsType.TextBox, hidden: false, defaultValue: 'default' }
    };
    component.paramDefs = { field: value };
    component.setForms();
    expect(component.paramDefs.field).toEqual(value);
  });

  it.each([DynamicFormsType.CheckBox, DynamicFormsType.LegacyCheckBox])(
    'round-trips arbitrary values for %s through checkbox option indexes',
    type => {
      const component = new NotebookParagraphDynamicFormsComponent();
      component.formDefs = {
        field: {
          name: 'field',
          type,
          hidden: false,
          defaultValue: [],
          options: [{ value: false }, { value: 42 }, { value: { id: 'a' } }]
        }
      };
      component.paramDefs = { field: [false, { id: 'a' }] };
      component.setForms();
      expect(component.checkboxValues.field).toEqual([0, 2]);
      expect(component.checkboxGroups.field).toEqual([
        { label: 'false', value: 0 },
        { label: '42', value: 1 },
        { label: '{"id":"a"}', value: 2 }
      ]);
      const onFormChange = vi.spyOn(component, 'onFormChange');
      component.checkboxChange([1, 2], 'field');
      expect(component.paramDefs.field).toEqual([42, { id: 'a' }]);
      expect(onFormChange).toHaveBeenCalledOnce();
    }
  );

  it.each([
    [DynamicFormsType.Input, DynamicFormsType.TextBox],
    [DynamicFormsType.LegacySelect, DynamicFormsType.Select],
    [DynamicFormsType.LegacyCheckBox, DynamicFormsType.CheckBox]
  ])('renders legacy %s with the %s control', (legacy, current) => {
    const component = new NotebookParagraphDynamicFormsComponent();
    expect(component.canonicalFormType(legacy)).toBe(current);
  });

  it('uses a default only for an absent parameter', () => {
    const component = new NotebookParagraphDynamicFormsComponent();
    component.formDefs = {
      field: { name: 'field', type: DynamicFormsType.Select, hidden: false, defaultValue: 42 }
    };
    component.paramDefs = {};
    component.setForms();
    expect(component.paramDefs.field).toBe(42);
  });
});
