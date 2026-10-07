/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import type { FormValue } from '../../../../../projects/zeppelin-sdk/src/interfaces/message-paragraph.interface';
import type { NotebookFormValue, NotebookCoreSnapshot } from './host-remote-contract';
import type { NotebookCoreRuntime } from './notebook-core';

export const acceptsSdkValues = (core: NotebookCoreRuntime, value: FormValue): NotebookFormValue => {
  core.apply({ type: 'note-forms-updated', noteForms: {}, noteParams: { field: value } });
  return value;
};

export const rejectsSnapshotWrites = (
  snapshot: NotebookCoreSnapshot,
  array: Extract<NotebookFormValue, readonly unknown[]>,
  object: Extract<NotebookFormValue, { readonly [key: string]: NotebookFormValue }>
): void => {
  // @ts-expect-error Snapshot parameter maps cannot be assigned to.
  snapshot.noteParams.field = 0;
  // @ts-expect-error Snapshot arrays cannot be appended to.
  array.push(0);
  // @ts-expect-error Snapshot object properties cannot be assigned to.
  object.field = 0;
};
