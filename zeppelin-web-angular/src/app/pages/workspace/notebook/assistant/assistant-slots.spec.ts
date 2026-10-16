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

import { ElementRef } from '@angular/core';
import { expect, it, vi } from 'vitest';
import { AssistantSlotDirective, AssistantSlots } from './assistant-slots';

it('registers and removes the React portal targets', () => {
  const slots = new AssistantSlots();
  const element = document.createElement('span');
  const directive = new AssistantSlotDirective(new ElementRef(element), slots);
  directive.assistantSlot = 'navigation';
  directive.ngOnChanges();
  expect(slots.slots.value).toEqual([{ element, kind: 'navigation' }]);
  directive.assistantSlot = 'panel';
  directive.ngOnChanges();
  expect(slots.slots.value).toEqual([{ element, kind: 'panel' }]);
  directive.ngOnDestroy();
  expect(slots.slots.value).toEqual([]);
});

it('requests panel close only while the panel is open', () => {
  const slots = new AssistantSlots();
  const close = vi.spyOn(slots.panelCloseRequests, 'next');
  slots.requestPanelClose();
  expect(close).not.toHaveBeenCalled();
  slots.setPanelOpen(true);
  slots.requestPanelClose();
  expect(close).toHaveBeenCalledOnce();
});
