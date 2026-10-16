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

import type { Link, Parent, Root, Text } from 'mdast';
import type { Plugin } from 'unified';

const paragraphIdPattern = /^paragraph_\d+_-?\d+$/;

export const isParagraphId = (value: string): boolean => paragraphIdPattern.test(value);

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
    } else if (child.type === 'inlineCode' && isParagraphId(child.value)) {
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

export const remarkParagraphReferences: Plugin<[], Root> = () => transformReferences;
