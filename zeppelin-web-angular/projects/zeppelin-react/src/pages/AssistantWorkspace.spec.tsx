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

import { act } from 'react';
import { fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AssistantShellProps } from '@zeppelin/sdk';
import { mount } from './AssistantWorkspace';

describe('AssistantWorkspace remote', () => {
  it('portals the navigation and panel, coordinates close, and shares the sidebar width', async () => {
    const root = document.createElement('div');
    const navigation = document.createElement('div');
    const panel = document.createElement('div');
    const onPanelVisibilityChange = vi.fn();
    const onPanelWidthChange = vi.fn();
    let requestClose = () => {};
    const props: AssistantShellProps = {
      noteId: 'note-a',
      slots: [
        { element: navigation, kind: 'navigation' },
        { element: panel, kind: 'panel' }
      ],
      panelWidth: 370,
      onPanelVisibilityChange,
      onPanelWidthChange,
      subscribePanelClose: listener => {
        requestClose = listener;
        return () => {
          requestClose = () => {};
        };
      }
    };
    let handle: ReturnType<typeof mount>;

    await act(async () => {
      handle = mount(root, props);
    });
    expect(navigation.querySelector('button')).not.toBeNull();
    expect(panel.textContent).toBe('');

    await act(async () => navigation.querySelector('button')?.click());
    expect(panel.querySelector('aside')).not.toBeNull();
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(true);
    expect(panel.style.getPropertyValue('--assistant-sidebar-width')).toBe('370px');

    await act(async () => {
      panel
        .querySelector<HTMLElement>('[role="separator"]')
        ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    });
    expect(onPanelWidthChange).toHaveBeenCalledWith(386);

    onPanelWidthChange.mockClear();
    const resizeHandle = panel.querySelector<HTMLElement>('[role="separator"]')!;
    await act(async () => fireEvent.pointerDown(resizeHandle, { button: 0, buttons: 1, clientX: 100 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 1, clientX: 140 }));
    expect(panel.style.getPropertyValue('--assistant-sidebar-width')).toBe('426px');
    expect(resizeHandle.getAttribute('aria-valuenow')).toBe('426');
    expect(onPanelWidthChange).not.toHaveBeenCalled();
    await act(async () => fireEvent.pointerUp(resizeHandle, { button: 0, clientX: 140 }));
    expect(onPanelWidthChange).toHaveBeenCalledExactlyOnceWith(426);

    onPanelWidthChange.mockClear();
    await act(async () => fireEvent.pointerDown(resizeHandle, { button: 0, buttons: 1, clientX: 100 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 1, clientX: 120 }));
    await act(async () => fireEvent.pointerMove(resizeHandle, { buttons: 0, clientX: 120 }));
    expect(onPanelWidthChange).toHaveBeenCalledExactlyOnceWith(446);

    await act(async () => requestClose());
    expect(panel.textContent).toBe('');
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);

    await act(async () => navigation.querySelector('button')?.click());
    await act(async () => handle.update({ ...props, noteId: 'note-b' }));
    expect(panel.textContent).toBe('');

    await act(async () => {
      handle.unmount();
    });
    expect(navigation.innerHTML).toBe('');
  });
});
