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

import { NotebookAclCapabilities, NotebookReadParagraph } from '@zeppelin/notebook-core';
import { DatasetType, GraphConfig, ParagraphConfigResult, ParagraphIResultsMsgItem } from '@zeppelin/sdk';

/**
 * Result types the read view renders with the shared result component. ANGULAR output binds to
 * live Angular objects, which a loaded snapshot does not carry, and NETWORK has no renderer in the
 * Angular UI. Both, and any type the SDK does not know, are shown as unsupported.
 */
const READ_VIEW_RENDERED_RESULT_TYPES: readonly DatasetType[] = [
  DatasetType.TABLE,
  DatasetType.TEXT,
  DatasetType.HTML,
  DatasetType.IMG,
  DatasetType.SVG,
  DatasetType.NULL
];

export type ReadResultView =
  | Readonly<{ kind: 'rendered'; result: ParagraphIResultsMsgItem; config: ParagraphConfigResult | undefined }>
  | Readonly<{ kind: 'unsupported'; type: string }>;

export type ReadParagraphView = Readonly<{
  paragraph: NotebookReadParagraph;
  results: readonly ReadResultView[];
}>;

const viewsByParagraph = new WeakMap<NotebookReadParagraph, ReadParagraphView>();

const toResultView = (paragraph: NotebookReadParagraph, index: number): ReadResultView => {
  const { type, data } = paragraph.resultMessages[index];
  if (!(READ_VIEW_RENDERED_RESULT_TYPES as readonly string[]).includes(type)) {
    return { kind: 'unsupported', type };
  }
  const key = String(index);
  const graph = Object.prototype.hasOwnProperty.call(paragraph.chartConfigs, key)
    ? paragraph.chartConfigs[key]
    : undefined;
  return {
    kind: 'rendered',
    result: { type: type as DatasetType, data },
    // The result component writes into its config while it renders, and the Core's copy is frozen.
    // This copy belongs to one render of one snapshot and is never sent back.
    config: graph === undefined ? undefined : { graph: structuredClone(graph) as GraphConfig }
  };
};

/**
 * The display model of a paragraph. It is derived from the Core's frozen paragraph and reused for
 * as long as that paragraph object is, so an unchanged paragraph is not rendered again.
 */
export const toReadParagraphView = (paragraph: NotebookReadParagraph): ReadParagraphView => {
  let view = viewsByParagraph.get(paragraph);
  if (view === undefined) {
    view = Object.freeze({
      paragraph,
      results: Object.freeze(paragraph.resultMessages.map((_, index) => toResultView(paragraph, index)))
    });
    viewsByParagraph.set(paragraph, view);
  }
  return view;
};

export type ReadAccess = 'owner' | 'writer' | 'runner' | 'reader' | 'none';

/** The strongest permission granted. Writers can run and runners can read, as on the server. */
export const toReadAccess = ({ own, write, run, read }: NotebookAclCapabilities): ReadAccess =>
  own ? 'owner' : write ? 'writer' : run ? 'runner' : read ? 'reader' : 'none';

/** The principal and roles the server matches against note permissions. */
export const toPrincipals = (ticket: Readonly<{ principal: string; roles: string }>): readonly string[] => {
  let roles: unknown;
  try {
    roles = JSON.parse(ticket.roles);
  } catch {
    roles = [];
  }
  return [
    ticket.principal,
    ...(Array.isArray(roles) ? roles.filter((role): role is string => typeof role === 'string') : [])
  ];
};
