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
  useEffect,
  useMemo,
  useReducer,
  type Dispatch,
  type ComponentPropsWithoutRef,
  type ReactNode
} from 'react';
import type { ExtraProps } from 'react-markdown';

import * as styles from './MarkdownAnswer.css';

type ImageApproval = Readonly<{ url: string; approved: boolean; owner: string | undefined }>;
type ImageApprovals = ReadonlyMap<string, ImageApproval>;
type ImageApprovalAction =
  | { type: 'mount' | 'approve'; occurrence: string; url: string; owner: string }
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
        approved: previous?.url === action.url ? previous.approved : false,
        owner: action.owner
      };
      break;
    case 'approve':
      if (previous?.url === action.url && previous.owner === action.owner && previous.approved) return state;
      approval = { url: action.url, approved: true, owner: action.owner };
      break;
    case 'unmount':
      if (previous?.owner !== action.owner) return state;
      approval = { ...previous, owner: undefined };
      break;
  }
  const next = new Map(state);
  next.set(action.occurrence, approval);
  return next;
};

// Committed consent belongs to a Markdown source occurrence, not a renderer's tag-ordinal key.
const ImageApprovalsContext = createContext<
  { imageApprovals: ImageApprovals; dispatch: Dispatch<ImageApprovalAction> } | undefined
>(undefined);

export const MarkdownAnswerImageScope = ({ children }: { children: ReactNode }) => {
  const [imageApprovals, dispatch] = useReducer(
    imageApprovalsReducer,
    undefined,
    () => new Map<string, ImageApproval>()
  );
  let hasUnmountedImage = false;
  imageApprovals.forEach(approval => {
    if (approval.owner === undefined) hasUnmountedImage = true;
  });
  // Same-batch rekeys reclaim their grant; prune real removals before children can read stale consent.
  if (hasUnmountedImage) dispatch({ type: 'prune' });
  const context = useMemo(() => ({ imageApprovals, dispatch }), [imageApprovals, dispatch]);
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
  const { imageApprovals, dispatch } = context;
  const occurrenceKey = occurrence ?? owner;
  const approval = imageApprovals.get(occurrenceKey);

  useEffect(() => {
    dispatch({ type: 'mount', occurrence: occurrenceKey, url: href, owner });
    return () => {
      dispatch({ type: 'unmount', occurrence: occurrenceKey, owner });
    };
  }, [dispatch, occurrenceKey, href, owner]);

  return {
    approved: approval?.url === href && approval.approved,
    approve: () => dispatch({ type: 'approve', occurrence: occurrenceKey, url: href, owner })
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
  const { approved, approve } = useImageApproval(occurrence, url.href, detailsId);

  return (
    <span className={styles.imagePlaceholder}>
      <span className={styles.imageDescription}>Image description: {alt || 'No description provided.'}</span>
      <span id={detailsId} className={styles.imageDetails}>
        <span>Host: {url.hostname}</span>
        <span>Image URL: {url.href}</span>
        <span>
          Loading this image sends a request to this host. The request may include information from the answer.
        </span>
      </span>
      {approved ? (
        <img src={url.href} alt={alt} title={title} className={styles.loadedImage} />
      ) : (
        <button
          type="button"
          className={styles.loadImage}
          aria-label={alt ? `Load image: ${alt}` : 'Load image'}
          aria-describedby={detailsId}
          onClick={approve}
        >
          Load image
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
