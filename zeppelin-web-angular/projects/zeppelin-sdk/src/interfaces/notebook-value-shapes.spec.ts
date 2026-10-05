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

import { assertType, expectTypeOf, it } from 'vitest';

import { NoteForms } from './message-notebook.interface';
import {
  AngularObjectClientBind,
  AngularObjectUpdated,
  DynamicFormParams,
  DynamicForms,
  DynamicFormsType,
  ParagraphEditorSetting,
  ParagraphItem,
  ParagraphSettings,
  RuntimeInfos
} from './message-paragraph.interface';

it('accepts arbitrary runtime-info properties and string-map entries', () => {
  assertType<RuntimeInfos>({
    sparkStages: {
      propertyName: 'sparkStages',
      label: 'Stages',
      tooltip: 'Spark stages',
      group: 'spark',
      interpreterSettingId: 'spark',
      values: [{ stage: '1', state: 'RUNNING' }]
    }
  });
  assertType<RuntimeInfos>({});
});

it('models GUI separately from interpreter editor settings', () => {
  expectTypeOf<ParagraphItem['settings']>().toEqualTypeOf<ParagraphSettings>();
  expectTypeOf<ParagraphEditorSetting>().not.toHaveProperty('params');
  expectTypeOf<ParagraphEditorSetting>().not.toHaveProperty('forms');
  assertType<ParagraphSettings>({
    params: { count: 0, enabled: false, selected: [42, true, { id: 'a' }], empty: null },
    forms: {}
  });
  assertType<DynamicFormParams>({ value: { nested: [1, false, null] } });
});

it('preserves the typed Input map and legacy form labels', () => {
  expectTypeOf<NoteForms>().toEqualTypeOf<DynamicForms>();
  assertType<NoteForms>({
    select: {
      type: DynamicFormsType.LegacySelect,
      name: 'select',
      hidden: false,
      defaultValue: 42,
      options: [{ value: 42 }, { value: { id: 'a' } }]
    },
    checkbox: {
      type: DynamicFormsType.LegacyCheckBox,
      name: 'checkbox',
      hidden: false,
      defaultValue: [true, { id: 'a' }]
    },
    input: { type: DynamicFormsType.Input, name: 'input', hidden: false, defaultValue: '' }
  });
});

it('does not constrain Angular object values to strings', () => {
  expectTypeOf<AngularObjectUpdated['value']>().toEqualTypeOf<unknown>();
  expectTypeOf<AngularObjectClientBind['value']>().toEqualTypeOf<unknown>();
});
