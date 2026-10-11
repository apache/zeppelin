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

import { SimpleChange, type ChangeDetectorRef } from '@angular/core';
import { expect, it, vi } from 'vitest';
import { AssistantHostComponent } from './assistant-host.component';
import { AssistantSlots } from './assistant-slots';

it('updates remote slots and width, then retires the panel when disabled', async () => {
  const slots = new AssistantSlots();
  const host = new AssistantHostComponent(slots, { markForCheck: vi.fn() } as unknown as ChangeDetectorRef);
  host.enabled = true;
  host.noteId = 'note-a';
  host.panelWidth = 370;
  host.ngOnChanges({ enabled: new SimpleChange(undefined, true, true) });
  host.ngOnInit();

  const navigation = document.createElement('span');
  slots.set({ element: navigation, kind: 'navigation' });
  await Promise.resolve();
  expect(host.assistantProps?.slots).toEqual([{ element: navigation, kind: 'navigation' }]);

  host.panelWidth = 400;
  host.ngOnChanges({ panelWidth: new SimpleChange(370, 400, false) });
  expect(host.assistantProps?.panelWidth).toBe(400);
  host.assistantProps?.onPanelVisibilityChange(true);
  expect(slots.panelOpen.value).toBe(true);

  const close = vi.spyOn(slots.panelCloseRequests, 'next');
  host.noteId = 'note-b';
  host.ngOnChanges({ noteId: new SimpleChange('note-a', 'note-b', false) });
  expect(close).toHaveBeenCalledOnce();

  host.enabled = false;
  host.ngOnChanges({ enabled: new SimpleChange(true, false, false) });
  expect(slots.panelOpen.value).toBe(false);
  expect(host.assistantProps).toBeNull();
  host.ngOnDestroy();
});
