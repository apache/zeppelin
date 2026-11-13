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

import { ChangeDetectorRef, ElementRef, SimpleChange } from '@angular/core';
import { EMPTY } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { NzModalService } from 'ng-zorro-antd/modal';
import type {
  MessageService,
  NgZService,
  NoteStatusService,
  NoteVarShareService,
  ShortcutService
} from '@zeppelin/services';

vi.mock('../../share/result/result.component', () => ({ NotebookParagraphResultComponent: class {} }));
vi.mock('./code-editor/code-editor.component', () => ({ NotebookParagraphCodeEditorComponent: class {} }));

import { NotebookParagraphComponent } from './paragraph.component';

describe('NotebookParagraphComponent', () => {
  describe.each(['selection', 'reorder'])('Paragraph focus after %s', reason => {
    let component: NotebookParagraphComponent;
    let host: HTMLElement;
    let input: HTMLInputElement;
    let outside: HTMLButtonElement;

    beforeEach(() => {
      vi.useFakeTimers();
      host = document.createElement('div');
      host.tabIndex = -1;
      input = document.createElement('input');
      host.append(input);
      outside = document.createElement('button');
      document.body.append(host, outside);
      component = new NotebookParagraphComponent(
        { receive: () => EMPTY, receiveEnvelope: () => EMPTY } as unknown as MessageService,
        new ElementRef(host),
        {} as NoteVarShareService,
        {} as NzModalService,
        {} as ShortcutService,
        {} as NoteStatusService,
        { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
        {} as NgZService
      );
      component.select = true;
    });

    afterEach(() => {
      component.ngOnDestroy();
      host.remove();
      outside.remove();
      vi.clearAllTimers();
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    const scheduleFocus = () => {
      component.ngOnChanges(
        reason === 'selection'
          ? { select: new SimpleChange(false, true, false) }
          : { index: new SimpleChange(1, 0, false) }
      );
    };

    it('preserves input focus acquired after scheduling', () => {
      outside.focus();
      scheduleFocus();
      input.focus();

      vi.runOnlyPendingTimers();

      expect(document.activeElement).toBe(input);
    });

    it('focuses the selected paragraph when focus is outside it', () => {
      outside.focus();
      scheduleFocus();

      vi.runOnlyPendingTimers();

      expect(document.activeElement).toBe(host);
    });
  });
});
