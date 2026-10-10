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
import type { ChangeDetectorRef } from '@angular/core';
import { AssistantSlots } from '../assistant/assistant-slots';
import { NotebookSidebarComponent } from './sidebar.component';

describe('NotebookSidebarComponent assistant coordination', () => {
  it('closes the file tree when the Assistant opens and closes the Assistant when the TOC opens', () => {
    const slots = new AssistantSlots();
    const sidebar = new NotebookSidebarComponent({ markForCheck: vi.fn() } as unknown as ChangeDetectorRef, slots);
    const open = vi.spyOn(sidebar.isSidebarOpenChange, 'emit');
    const closeAssistant = vi.spyOn(slots.panelCloseRequests, 'next');
    sidebar.ngOnInit();

    sidebar.setOrToggleSidebarState(sidebar.SidebarState.FILE_TREE);
    slots.setPanelOpen(true);
    expect(sidebar.sidebarState).toBe(sidebar.SidebarState.CLOSED);
    sidebar.setOrToggleSidebarState(sidebar.SidebarState.TOC);
    expect(closeAssistant).toHaveBeenCalledOnce();
    expect(open.mock.calls).toEqual([[true], [false], [true]]);

    sidebar.ngOnDestroy();
  });
});
