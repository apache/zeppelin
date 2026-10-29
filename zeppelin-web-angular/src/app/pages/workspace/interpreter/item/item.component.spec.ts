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

import type { ChangeDetectorRef } from '@angular/core';
import { describe, expect, it } from 'vitest';

import type { SecurityService, TicketService, InterpreterService } from '@zeppelin/services';

import type { InterpreterComponent } from '../interpreter.component';
import { InterpreterItemComponent } from './item.component';

const createComponent = () =>
  new InterpreterItemComponent(
    {} as InterpreterComponent,
    {} as TicketService,
    {} as SecurityService,
    {} as InterpreterService,
    {} as ChangeDetectorRef
  );

describe('InterpreterItemComponent.displayPropertyValue', () => {
  it.each([false, true])('preserves the boolean value %s', value => {
    const component = createComponent();

    expect(component.displayPropertyValue(value)).toBe(value);
  });

  it.each([null, undefined])('renders an absent value as empty', value => {
    const component = createComponent();

    expect(component.displayPropertyValue(value)).toBe('');
  });
});
