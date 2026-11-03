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

import { assertType, expectTypeOf, it } from 'vitest';

import { MessageReceiveDataTypeMap, MessageSendDataTypeMap } from './message-data-type-map.interface';
import { ConvertNote, ConvertedNote } from './message-notebook.interface';
import { OP } from './message-operator.interface';
import { DatasetType, ParagraphAppendOutput, ParagraphUpdateOutput } from './message-paragraph.interface';

it('declares the asymmetric paragraph output payloads sent by the server', () => {
  const append: MessageReceiveDataTypeMap[OP.PARAGRAPH_APPEND_OUTPUT] = {
    noteId: 'note',
    paragraphId: 'paragraph',
    index: 0,
    data: 'chunk'
  };
  assertType<MessageReceiveDataTypeMap[OP.PARAGRAPH_UPDATE_OUTPUT]>({
    ...append,
    type: DatasetType.TEXT
  });
  expectTypeOf<MessageReceiveDataTypeMap[OP.PARAGRAPH_APPEND_OUTPUT]>().toEqualTypeOf<ParagraphAppendOutput>();
  expectTypeOf<MessageReceiveDataTypeMap[OP.PARAGRAPH_APPEND_OUTPUT]>().not.toHaveProperty('type');
  expectTypeOf<MessageReceiveDataTypeMap[OP.PARAGRAPH_UPDATE_OUTPUT]>().toEqualTypeOf<ParagraphUpdateOutput>();
  expectTypeOf<MessageReceiveDataTypeMap[OP.PARAGRAPH_UPDATE_OUTPUT]>()
    .toHaveProperty('type')
    .toEqualTypeOf<DatasetType>();
});

it('requires the notebook conversion request fields and a serialized response', () => {
  assertType<MessageSendDataTypeMap[OP.CONVERT_NOTE_NBFORMAT]>({
    noteId: 'note',
    noteName: 'folder/notebook'
  });
  assertType<MessageReceiveDataTypeMap[OP.CONVERTED_NOTE_NBFORMAT]>({
    nbformat: '{"cells":[],"nbformat":4,"nbformat_minor":0}',
    noteName: 'folder/notebook'
  });
  expectTypeOf<MessageSendDataTypeMap[OP.CONVERT_NOTE_NBFORMAT]>().toEqualTypeOf<ConvertNote>();
  expectTypeOf<MessageSendDataTypeMap[OP.CONVERT_NOTE_NBFORMAT]>().toEqualTypeOf<{
    noteId: string;
    noteName: string;
  }>();
  expectTypeOf<MessageReceiveDataTypeMap[OP.CONVERTED_NOTE_NBFORMAT]>().toEqualTypeOf<ConvertedNote>();
  expectTypeOf<MessageReceiveDataTypeMap[OP.CONVERTED_NOTE_NBFORMAT]>().toEqualTypeOf<{
    nbformat: string;
    noteName: string;
  }>();
  expectTypeOf<{ noteId: string }>().not.toMatchTypeOf<ConvertNote>();
  expectTypeOf<{ nbformat: { cells: [] }; noteName: string }>().not.toMatchTypeOf<ConvertedNote>();
});
