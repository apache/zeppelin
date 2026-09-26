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

import { ChangeDetectorRef } from '@angular/core';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalService } from 'ng-zorro-antd/modal';
import { describe, expect, it, vi } from 'vitest';

import { Interpreter } from '@zeppelin/interfaces';
import { InterpreterService } from '@zeppelin/services';

import { InterpreterComponent } from './interpreter.component';

// InterpreterSettingManager rejects new setting names outside ^[-_a-zA-Z0-9]+$, and every bundled
// interpreter group fits it, so the fixtures stay within that set.
const NAMES = ['spark', 'spark-sql', 'python', 'jdbc', 'md'];

const createComponent = () => {
  const cdr = { markForCheck: vi.fn() } as unknown as ChangeDetectorRef;
  const component = new InterpreterComponent(
    {} as InterpreterService,
    cdr,
    {} as NzModalService,
    {} as NzMessageService
  );
  component.interpreterSettings = NAMES.map(name => ({ name }) as Interpreter);
  component.filteredInterpreterSettings = component.interpreterSettings;
  return component;
};

const filteredNames = (component: InterpreterComponent) => component.filteredInterpreterSettings.map(e => e.name);

describe('InterpreterComponent.filterInterpreters', () => {
  it.each(['(', '[', '*', '.', '\\'])('matches the metacharacter %s literally without throwing', query => {
    const component = createComponent();

    expect(() => component.filterInterpreters(query)).not.toThrow();
    expect(filteredNames(component)).toEqual([]);
  });

  it('does not treat the query as a pattern', () => {
    const component = createComponent();

    // As a pattern, `.` matches any character and `sp.rk` matches `spark`.
    component.filterInterpreters('sp.rk');

    expect(filteredNames(component)).toEqual([]);
  });

  it('matches names case-insensitively', () => {
    const component = createComponent();

    component.filterInterpreters('SPARK');

    expect(filteredNames(component)).toEqual(['spark', 'spark-sql']);
  });

  it('restores the full list when the query is cleared', () => {
    const component = createComponent();
    component.filterInterpreters('jdbc');

    component.filterInterpreters('');

    expect(filteredNames(component)).toEqual(NAMES);
  });
});
