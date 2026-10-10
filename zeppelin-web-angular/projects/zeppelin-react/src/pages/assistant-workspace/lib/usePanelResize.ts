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

import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { NOTEBOOK_SIDEBAR_WIDTH } from '@zeppelin/sdk';

const KEY_STEP_PX = 16;
const clamp = (width: number) =>
  Math.min(NOTEBOOK_SIDEBAR_WIDTH.max, Math.max(NOTEBOOK_SIDEBAR_WIDTH.min, Math.round(width)));
const setWidthOn = (element: HTMLElement | undefined, width: number) =>
  element?.style.setProperty('--assistant-panel-width', `${width}px`);

/**
 * The panel's width, shared with the notebook sidebar through the host, and the props of its resize handle. A drag
 * writes the width straight to `element` so the panel does not re-render per pointer move, and reports it once at the
 * end; arrow keys step it. The host's width wins whenever it changes.
 */
export const usePanelResize = (
  element: HTMLElement | undefined,
  hostWidth: number | undefined,
  onChange?: (width: number) => void
) => {
  const [width, setWidth] = useState(() => clamp(hostWidth ?? NOTEBOOK_SIDEBAR_WIDTH.initial));
  const [resizing, setResizing] = useState(false);
  const drag = useRef<{ startX: number; startWidth: number; latest: number } | null>(null);

  useEffect(() => {
    if (hostWidth !== undefined) setWidth(clamp(hostWidth));
  }, [hostWidth]);
  useLayoutEffect(() => {
    setWidthOn(element, width);
    return () => {
      element?.style.removeProperty('--assistant-panel-width');
    };
  }, [element, width]);

  const commit = (next: number) => {
    setWidth(next);
    if (next !== width) onChange?.(next);
  };
  const end = () => {
    if (!drag.current) return;
    const { latest } = drag.current;
    drag.current = null;
    setResizing(false);
    commit(latest);
  };

  const handleProps = {
    role: 'separator',
    'aria-orientation': 'vertical',
    'aria-valuenow': width,
    'aria-valuetext': `${width} pixels wide`,
    'aria-valuemin': NOTEBOOK_SIDEBAR_WIDTH.min,
    'aria-valuemax': NOTEBOOK_SIDEBAR_WIDTH.max,
    tabIndex: 0,
    onPointerDown: (event: PointerEvent<HTMLElement>) => {
      if (event.button !== 0) return;
      event.preventDefault();
      // Captured, so the move and up events keep coming here even when the pointer leaves the strip.
      event.currentTarget.setPointerCapture?.(event.pointerId);
      drag.current = { startX: event.clientX, startWidth: width, latest: width };
      setResizing(true);
    },
    onPointerMove: (event: PointerEvent<HTMLElement>) => {
      if (!drag.current) return;
      // Released where the strip could not see it (no pointer capture): the drag is over.
      if (event.buttons === 0) {
        end();
        return;
      }
      drag.current.latest = clamp(drag.current.startWidth + event.clientX - drag.current.startX);
      setWidthOn(element, drag.current.latest);
      event.currentTarget.setAttribute('aria-valuenow', String(drag.current.latest));
      event.currentTarget.setAttribute('aria-valuetext', `${drag.current.latest} pixels wide`);
    },
    onPointerUp: end,
    onPointerCancel: end,
    onKeyDown: (event: KeyboardEvent<HTMLElement>) => {
      const target = {
        ArrowLeft: width - KEY_STEP_PX,
        ArrowRight: width + KEY_STEP_PX,
        Home: NOTEBOOK_SIDEBAR_WIDTH.min,
        End: NOTEBOOK_SIDEBAR_WIDTH.max
      }[event.key];
      if (target === undefined) return;
      event.preventDefault();
      commit(clamp(target));
    }
  } as const;

  return { width, resizing, handleProps };
};
