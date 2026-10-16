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
import { fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { NOTEBOOK_SIDEBAR_WIDTH, type AssistantShellProps } from '@zeppelin/sdk';
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

describe('AssistantWorkspace', () => {
  let cleanup: (() => Promise<void>) | undefined;

  afterEach(async () => {
    await cleanup?.();
    cleanup = undefined;
  });

  const setup = async () => {
    const navigation = document.createElement('div');
    const panel = document.createElement('div');
    document.body.append(navigation, panel);
    const onPanelVisibilityChange = vi.fn();
    const onPanelWidthChange = vi.fn();
    let handle: ReturnType<typeof mount>;
    await act(async () => {
      handle = mount(document.createElement('div'), {
        noteId: 'note-a',
        slots: [
          { element: navigation, kind: 'navigation' },
          { element: panel, kind: 'panel' }
        ],
        panelWidth: 370,
        onPanelVisibilityChange,
        onPanelWidthChange,
        subscribePanelClose: () => () => {}
      });
    });
    const unmount = async () => {
      await act(async () => handle.unmount());
      navigation.remove();
      panel.remove();
    };
    cleanup = unmount;
    const toggle = () => screen.getByRole('button', { name: 'Toggle AI Assistant' });
    const open = async () => act(async () => fireEvent.click(toggle()));
    return { toggle, open, unmount, onPanelVisibilityChange, onPanelWidthChange };
  };

  it('marks the toggle as pressed while the panel is open', async () => {
    const { toggle, open } = await setup();
    expect(toggle().getAttribute('aria-pressed')).toBe('false');

    await open();
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('complementary', { name: 'AI Assistant workspace' })).toBeTruthy();
  });

  it('closes on Escape and returns focus to the toggle', async () => {
    const { toggle, open, onPanelVisibilityChange } = await setup();
    await open();

    await act(async () => fireEvent.keyDown(screen.getByRole('complementary'), { key: 'Escape' }));
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(document.activeElement).toBe(toggle());
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);
  });

  it('returns focus to the toggle when the close button is used', async () => {
    const { toggle, open } = await setup();
    await open();

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Close AI Assistant' })));
    expect(screen.queryByRole('complementary')).toBeNull();
    expect(document.activeElement).toBe(toggle());
  });

  it('keeps keyboard resizing within the sidebar limits', async () => {
    const { open, onPanelWidthChange } = await setup();
    await open();
    const handle = screen.getByRole('separator', { name: 'Resize AI Assistant panel' });

    await act(async () => fireEvent.keyDown(handle, { key: 'End' }));
    expect(onPanelWidthChange).toHaveBeenLastCalledWith(NOTEBOOK_SIDEBAR_WIDTH.max);
    await act(async () => fireEvent.keyDown(handle, { key: 'ArrowRight' }));
    expect(handle.getAttribute('aria-valuenow')).toBe(String(NOTEBOOK_SIDEBAR_WIDTH.max));

    await act(async () => fireEvent.keyDown(handle, { key: 'Home' }));
    expect(onPanelWidthChange).toHaveBeenLastCalledWith(NOTEBOOK_SIDEBAR_WIDTH.min);
  });

  it('reports the panel as closed when the remote unmounts while open', async () => {
    const { open, unmount, onPanelVisibilityChange } = await setup();
    await open();
    cleanup = undefined;

    await unmount();
    expect(onPanelVisibilityChange).toHaveBeenLastCalledWith(false);
  });
});
