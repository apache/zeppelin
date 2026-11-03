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

import { useEffect, useRef, useState } from 'react';
import { createRoot, Root } from 'react-dom/client';
import type { NotebookReadPort, NotebookReadSnapshot } from '@zeppelin/notebook-core';
import { ReactErrorBoundary } from '@/components/paragraph/ReactErrorBoundary';
import { NotebookReadProvider, useNotebookReadSnapshot } from '@/notebook/NotebookCoreProvider';

export interface NotebookReadSkeletonProps {
  core: NotebookReadPort;
  /** The `paragraph` query parameter of the notebook URL. */
  anchor?: string | null;
  /** Test-only: throw while mounting, or on the first render after the mount completed. */
  failure?: 'mount' | 'after-mount' | null;
  /** Called once the first render has committed. This is not notebook readiness. */
  onMounted?: () => void;
  onError?: (error: unknown) => void;
}

const STATUS_TEXT: Record<NotebookReadSnapshot['status'], string> = {
  idle: 'No note selected',
  loading: 'Loading the note',
  ready: 'Note loaded',
  'not-found': 'Note not found',
  'access-denied': 'You do not have permission to read this note',
  failed: 'The note could not be loaded',
  disposed: 'Closed'
};

const Skeleton = ({ anchor, failure, onMounted }: Omit<NotebookReadSkeletonProps, 'core' | 'onError'>) => {
  const snapshot = useNotebookReadSnapshot();
  const [mounted, setMounted] = useState(false);
  // Read through a ref so a host passing a new callback on update() does not report the mount again.
  const onMountedRef = useRef(onMounted);
  onMountedRef.current = onMounted;

  useEffect(() => {
    onMountedRef.current?.();
    setMounted(true);
  }, []);

  if (failure === 'mount' || (failure === 'after-mount' && mounted)) {
    throw new Error(`NotebookReadSkeleton: injected ${failure} failure`);
  }

  return (
    <section aria-label="Notebook read-only preview" data-testid="notebook-read-skeleton">
      <p role="status" data-read-status={snapshot.status}>
        {STATUS_TEXT[snapshot.status]}
      </p>
      <dl>
        <dt>Note</dt>
        <dd>{snapshot.noteId}</dd>
        <dt>Revision</dt>
        <dd>{snapshot.revisionId ?? 'Live'}</dd>
        <dt>Paragraph</dt>
        <dd>{anchor ?? 'None'}</dd>
      </dl>
    </section>
  );
};

export const NotebookReadSkeleton = ({ core, onError, ...props }: NotebookReadSkeletonProps) => (
  <ReactErrorBoundary onError={onError}>
    <NotebookReadProvider core={core}>
      <Skeleton {...props} />
    </NotebookReadProvider>
  </ReactErrorBoundary>
);

export interface NotebookReadSkeletonMountHandle {
  update: (props: NotebookReadSkeletonProps) => void;
  unmount: () => void;
}

export const mount = (
  element: HTMLElement,
  initialProps: NotebookReadSkeletonProps
): NotebookReadSkeletonMountHandle => {
  if (!element) {
    throw new Error('Mount element is required');
  }

  const root: Root = createRoot(element);
  root.render(<NotebookReadSkeleton {...initialProps} />);

  return {
    update: (props: NotebookReadSkeletonProps) => {
      root.render(<NotebookReadSkeleton {...props} />);
    },
    unmount: () => {
      root.unmount();
    }
  };
};
