/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
  createContext,
  useContext,
  useId,
  useLayoutEffect,
  useMemo,
  useReducer,
  useRef,
  type Dispatch,
  type ComponentPropsWithoutRef,
  type ReactNode,
  type RefObject
} from 'react';
import type { ExtraProps } from 'react-markdown';

import * as styles from './MarkdownAnswer.css';

type ImageStatus = 'unapproved' | 'requested' | 'failed';
type ImageApproval = Readonly<{ url: string; status: ImageStatus; owner: string | undefined }>;
type ImageApprovals = ReadonlyMap<string, ImageApproval>;
type ImageApprovalAction =
  | { type: 'mount' | 'approve' | 'fail' | 'retry'; occurrence: string; url: string; owner: string }
  | { type: 'unmount'; occurrence: string; owner: string }
  | { type: 'prune' };

const imageApprovalsReducer = (state: ImageApprovals, action: ImageApprovalAction): ImageApprovals => {
  if (action.type === 'prune') {
    let next: Map<string, ImageApproval> | undefined;
    state.forEach((approval, occurrence) => {
      if (approval.owner !== undefined) return;
      next ??= new Map(state);
      next.delete(occurrence);
    });
    return next ?? state;
  }

  const previous = state.get(action.occurrence);
  let approval: ImageApproval;
  switch (action.type) {
    case 'mount':
      if (previous?.url === action.url && previous.owner === action.owner) return state;
      approval = {
        url: action.url,
        status: previous?.url === action.url ? previous.status : 'unapproved',
        owner: action.owner
      };
      break;
    case 'approve':
      if (previous?.url === action.url && previous.owner === action.owner && previous.status !== 'unapproved') {
        return state;
      }
      approval = { url: action.url, status: 'requested', owner: action.owner };
      break;
    case 'fail':
    case 'retry': {
      if (previous?.url !== action.url || previous.owner !== action.owner) return state;

      const expectedStatus = action.type === 'fail' ? 'requested' : 'failed';
      if (previous.status !== expectedStatus) return state;

      const nextStatus = action.type === 'fail' ? 'failed' : 'requested';
      approval = { ...previous, status: nextStatus };
      break;
    }
    case 'unmount':
      if (previous?.owner !== action.owner) return state;
      approval = { ...previous, owner: undefined };
      break;
  }
  const next = new Map(state);
  next.set(action.occurrence, approval);
  return next;
};

type FocusHandoff = { occurrence: string; href: string };

// Committed consent belongs to a Markdown source occurrence, not a renderer's tag-ordinal key.
const ImageApprovalsContext = createContext<
  | {
      imageApprovals: ImageApprovals;
      dispatch: Dispatch<ImageApprovalAction>;
      focusHandoff: RefObject<FocusHandoff | null>;
    }
  | undefined
>(undefined);

export const MarkdownAnswerImageScope = ({ children }: { children: ReactNode }) => {
  const [imageApprovals, dispatch] = useReducer(
    imageApprovalsReducer,
    undefined,
    () => new Map<string, ImageApproval>()
  );
  const focusHandoff = useRef<FocusHandoff>(null);
  useLayoutEffect(() => {
    // Descendant layout effects have restored any same-commit handoff. Do not carry it into a later mount.
    focusHandoff.current = null;
  });
  let hasUnmountedImage = false;
  imageApprovals.forEach(approval => {
    if (approval.owner === undefined) hasUnmountedImage = true;
  });
  // Same-batch rekeys reclaim their grant; prune real removals before children can read stale consent.
  if (hasUnmountedImage) dispatch({ type: 'prune' });
  const context = useMemo(() => ({ imageApprovals, dispatch, focusHandoff }), [imageApprovals, dispatch]);
  return <ImageApprovalsContext.Provider value={context}>{children}</ImageApprovalsContext.Provider>;
};

const resolveImageUrl = (src: ComponentPropsWithoutRef<'img'>['src'], base: string | undefined): URL | undefined => {
  if (typeof src !== 'string' || !src.trim()) return undefined;
  try {
    const url = base === undefined ? new URL(src) : new URL(src, base);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url : undefined;
  } catch {
    return undefined;
  }
};

const useImageApproval = (occurrence: string | undefined, href: string, owner: string) => {
  const context = useContext(ImageApprovalsContext);
  if (context === undefined) throw new Error('MarkdownAnswerImage must be rendered within MarkdownAnswerImageScope.');
  const { imageApprovals, dispatch, focusHandoff } = context;
  const occurrenceKey = occurrence ?? owner;
  const approval = imageApprovals.get(occurrenceKey);

  // Claim ownership before ancestor layout callbacks or native image events can dispatch retry or fail.
  useLayoutEffect(() => {
    dispatch({ type: 'mount', occurrence: occurrenceKey, url: href, owner });
    return () => {
      dispatch({ type: 'unmount', occurrence: occurrenceKey, owner });
    };
  }, [dispatch, occurrenceKey, href, owner]);

  return {
    status: approval?.url === href ? approval.status : 'unapproved',
    focusHandoff,
    approve: () => dispatch({ type: 'approve', occurrence: occurrenceKey, url: href, owner }),
    fail: () => dispatch({ type: 'fail', occurrence: occurrenceKey, url: href, owner }),
    retry: () => dispatch({ type: 'retry', occurrence: occurrenceKey, url: href, owner })
  };
};

type LoadableImageProps = {
  url: URL;
  alt: string;
  title: string | undefined;
  occurrence: string | undefined;
};

const LoadableImage = ({ url, alt, title, occurrence }: LoadableImageProps) => {
  const detailsId = useId();
  const href = url.href;
  const { status, focusHandoff, approve, fail, retry } = useImageApproval(occurrence, href, detailsId);
  const placeholderRef = useRef<HTMLSpanElement>(null);
  const descriptionRef = useRef<HTMLSpanElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const failed = status === 'failed';
  const actionLabel = failed ? 'Retry image' : 'Load image';

  // Transfer focus across renderer rekeys only for the same occurrence and destination.
  useLayoutEffect(() => {
    const placeholder = placeholderRef.current;
    if (!placeholder || occurrence === undefined) return;

    const document = placeholder.ownerDocument;
    const handoff = focusHandoff.current;
    if (handoff?.occurrence === occurrence && handoff.href === href) {
      focusHandoff.current = null;
      if (document.activeElement === document.body) {
        (buttonRef.current ?? descriptionRef.current)?.focus();
      }
    }

    // Capture focus in layout cleanup while the old DOM still exists.
    return () => {
      if (placeholder.contains(document.activeElement)) focusHandoff.current = { occurrence, href };
    };
  }, [focusHandoff, href, occurrence]);

  // Return from the pending description to Retry only if the user has not moved focus away.
  useLayoutEffect(() => {
    const description = descriptionRef.current;
    if (failed && description?.ownerDocument.activeElement === description) {
      buttonRef.current?.focus();
    }
  }, [failed]);

  return (
    <span ref={placeholderRef} className={styles.imagePlaceholder}>
      <span ref={descriptionRef} tabIndex={-1} className={styles.loadableImageDescription}>
        Image description: {alt || 'No description provided.'}
      </span>
      <span id={detailsId} className={styles.imageDetails}>
        <span>Host: {url.hostname}</span>
        <span>Image URL: {url.href}</span>
        <span>
          Loading this image sends a request to this host. The request may include information from the answer.
          <span role="status" className={styles.imageFailure}>
            {failed ? 'Image could not be loaded.' : null}
          </span>
        </span>
      </span>
      {status === 'requested' ? (
        <img src={url.href} alt={alt} title={title} className={styles.loadedImage} onError={fail} />
      ) : (
        <button
          type="button"
          ref={buttonRef}
          className={styles.loadImage}
          aria-label={alt ? `${actionLabel}: ${alt}` : actionLabel}
          aria-describedby={detailsId}
          onClick={({ currentTarget }) => {
            // Keep focus in the image card while the action button is not rendered.
            if (currentTarget.ownerDocument.activeElement === currentTarget) {
              descriptionRef.current?.focus();
            }
            if (failed) {
              retry();
            } else {
              approve();
            }
          }}
        >
          {actionLabel}
        </button>
      )}
    </span>
  );
};

export const MarkdownAnswerImage = ({ node, src, alt, title }: ComponentPropsWithoutRef<'img'> & ExtraProps) => {
  const base = typeof document === 'undefined' ? undefined : document.baseURI;
  const url = useMemo(() => resolveImageUrl(src, base), [src, base]);
  const start = node?.position?.start.offset;
  const end = node?.position?.end.offset;
  // Reference definitions can acquire a title later without changing the image occurrence.
  const occurrence = useMemo(
    () => (start === undefined || end === undefined ? undefined : JSON.stringify([start, end, alt])),
    [start, end, alt]
  );
  if (!url) {
    return (
      <span className={styles.imagePlaceholder}>
        <span className={styles.imageDescription}>Image description: {alt || 'No description provided.'}</span>
        <span>Image unavailable: only HTTP(S) image URLs can be loaded.</span>
      </span>
    );
  }
  return (
    <LoadableImage key={`${occurrence}:${url.href}`} occurrence={occurrence} url={url} alt={alt ?? ''} title={title} />
  );
};
