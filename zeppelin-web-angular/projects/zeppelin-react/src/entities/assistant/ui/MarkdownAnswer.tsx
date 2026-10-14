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

import { createContext, useContext, useId, useMemo, type ComponentPropsWithoutRef, type MouseEvent } from 'react';
import Markdown, { type Components, type ExtraProps } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Link, Parent, Root, Text } from 'mdast';
import type { Plugin, PluggableList } from 'unified';

import { visuallyHidden } from '@/shared/ui/assistant-theme/AssistantScope.css';
import * as styles from './MarkdownAnswer.css';

export interface MarkdownAnswerProps {
  /** Markdown text of the answer. While it is still arriving, it may end inside a code fence. */
  content: string;
  /** Describes a paragraph the answer refers to by ID, for its button. A reference it cannot describe is left as written, without a button. */
  describeParagraph?: (paragraphId: string) => { text: string; name: string } | undefined;
  /** Called when a paragraph button is clicked. Without it, references stay plain text. */
  onOpenParagraph?: (paragraphId: string, label: string) => void;
}

type AnswerContextValue = Pick<MarkdownAnswerProps, 'describeParagraph' | 'onOpenParagraph'> & {
  /** Prefix of the footnote ids of this answer, so that two answers on one page do not share them. */
  footnotePrefix: string;
};

const AnswerContext = createContext<AnswerContextValue>({ footnotePrefix: '' });
const paragraphIdPattern = /^paragraph_\d+_-?\d+$/;

const textReferences = (node: Text): Array<Text | Link> | undefined => {
  // Match a complete ID without splitting Unicode letters, numbers or combining marks.
  const pattern = new RegExp('(^|[^\\p{L}\\p{N}\\p{M}_+-])(paragraph_\\d+_-?\\d+)(?![\\p{L}\\p{N}\\p{M}_+-])', 'gu');
  let references: Array<Text | Link> | undefined;
  let offset = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(node.value)) !== null) {
    const id = match[2];
    const start = match.index + match[1].length;
    references ??= [];
    if (start > offset) {
      references.push({ type: 'text', value: node.value.slice(offset, start) });
    }
    references.push({ type: 'link', url: `#${id}`, children: [{ type: 'text', value: id }] });
    offset = start + id.length;
  }

  if (references && offset < node.value.length) {
    references.push({ type: 'text', value: node.value.slice(offset) });
  }
  return references;
};

const transformReferences = (parent: Parent): void => {
  let children: Parent['children'] | undefined;

  for (let index = 0; index < parent.children.length; index++) {
    const child = parent.children[index];
    let references: Array<Text | Link> | undefined;
    if (child.type === 'text') {
      references = textReferences(child);
    } else if (child.type === 'inlineCode' && paragraphIdPattern.test(child.value)) {
      references = [{ type: 'link', url: `#${child.value}`, children: [child] }];
    } else if (child.type !== 'link' && child.type !== 'linkReference' && 'children' in child) {
      transformReferences(child);
    }

    if (references) {
      children ??= parent.children.slice(0, index);
      children.push(...references);
    } else {
      children?.push(child);
    }
  }

  if (children) parent.children = children;
};

const remarkParagraphReferences: Plugin<[], Root> = () => transformReferences;
const remarkPlugins: PluggableList = [remarkGfm, remarkParagraphReferences];

// remark-rehype gives the footnotes heading this fixed id and points every footnote reference at it.
const footnoteLabelId = 'footnote-label';

const AnswerLink = ({ node: _node, href, children, title, ...props }: ComponentPropsWithoutRef<'a'> & ExtraProps) => {
  const { describeParagraph, onOpenParagraph, footnotePrefix } = useContext(AnswerContext);

  if (!href) return <>{children}</>;

  const fragment = href.startsWith('#');
  const paragraphId = fragment ? href.slice(1) : undefined;
  if (paragraphId && paragraphIdPattern.test(paragraphId)) {
    const description = describeParagraph?.(paragraphId);
    if (description && onOpenParagraph) {
      return (
        <button
          type="button"
          className={styles.paragraphReference}
          aria-label={`${description.text}: show ${description.name} in the notebook`}
          onClick={() => onOpenParagraph(paragraphId, description.name)}
        >
          {description.text}
        </button>
      );
    }
    return <>{children}</>;
  }

  // The URL hash belongs to the router, so show the target in the page instead of navigating to it.
  const showTarget = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const target = document.getElementById(href.slice(1));
    if (!target) return;
    target.scrollIntoView?.({ block: 'nearest' });
    // Keyboard focus follows the target, as it does after a native jump to a fragment.
    target.tabIndex = -1;
    target.focus({ preventScroll: true });
  };

  // react-markdown has already applied its default URL safety transform.
  return (
    <a
      {...props}
      aria-describedby={
        props['aria-describedby'] === footnoteLabelId
          ? `${footnotePrefix}${footnoteLabelId}`
          : props['aria-describedby']
      }
      href={href}
      onClick={fragment ? showTarget : undefined}
      title={title}
      target={fragment ? undefined : '_blank'}
      rel={fragment ? undefined : 'noopener noreferrer'}
    >
      {children}
    </a>
  );
};

const AnswerCodeBlock = ({ node, children, ...props }: ComponentPropsWithoutRef<'pre'> & ExtraProps) => {
  const code = node?.children.find(child => child.type === 'element' && child.tagName === 'code');
  const classNames = code?.type === 'element' ? code.properties.className : undefined;
  const languageClass = Array.isArray(classNames)
    ? classNames.find(className => typeof className === 'string' && className.startsWith('language-'))
    : undefined;
  const language = typeof languageClass === 'string' ? languageClass.slice('language-'.length) : 'Code';

  return (
    <div className={styles.codeBlock}>
      <div className={styles.codeLanguage}>{language}</div>
      <pre {...props} className={styles.codeSource} tabIndex={0} role="region" aria-label={`${language} block`}>
        {children}
      </pre>
    </div>
  );
};

const AnswerTable = ({ node: _node, children, ...props }: ComponentPropsWithoutRef<'table'> & ExtraProps) => {
  return (
    <div className={styles.tableScroll} role="region" aria-label="Scrollable table" tabIndex={0}>
      <table {...props}>{children}</table>
    </div>
  );
};

const AnswerHeading = ({ node: _node, id, ...props }: ComponentPropsWithoutRef<'h2'> & ExtraProps) => {
  const { footnotePrefix } = useContext(AnswerContext);
  return <h2 {...props} id={id === footnoteLabelId ? `${footnotePrefix}${id}` : id} />;
};

const components: Components = {
  h2: AnswerHeading,
  a: AnswerLink,
  pre: AnswerCodeBlock,
  table: AnswerTable
};

/** Renders the Markdown of an assistant answer. HTML is shown as text and unsafe link URLs are removed. */
export const MarkdownAnswer = ({ content, describeParagraph, onOpenParagraph }: MarkdownAnswerProps) => {
  // Footnote ids must differ between the answers on one page.
  const footnotePrefix = `${useId()}-`;
  // The whole answer is parsed again each time this renders. Keying the parsed element on the text means typing in a
  // sibling input does not parse it.
  const markdown = useMemo(
    () => (
      <Markdown
        remarkPlugins={remarkPlugins}
        components={components}
        remarkRehypeOptions={{
          clobberPrefix: footnotePrefix,
          footnoteLabelProperties: { className: [visuallyHidden] }
        }}
      >
        {content}
      </Markdown>
    ),
    [content, footnotePrefix]
  );

  return (
    <AnswerContext.Provider value={{ describeParagraph, onOpenParagraph, footnotePrefix }}>
      <div className={styles.answer}>{markdown}</div>
    </AnswerContext.Provider>
  );
};
