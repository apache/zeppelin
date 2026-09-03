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

import { describe, expect, it, vi } from 'vitest';

import type { MessageReceiveDataTypeMap } from './interfaces/message-data-type-map.interface';
import { OP } from './interfaces/message-operator.interface';
import type { WebSocketMessage } from './interfaces/websocket-message.interface';
import { Message } from './message';

const asReceivedMessage = (message: unknown): WebSocketMessage<MessageReceiveDataTypeMap> =>
  message as WebSocketMessage<MessageReceiveDataTypeMap>;

describe('Message.receive', () => {
  it('passes a non-removal job update with noteName', () => {
    const message = new Message();
    const listener = vi.fn();
    const data = {
      noteRunningJobs: {
        jobs: [
          {
            noteId: 'note-1',
            noteName: 'Test Note',
            isRemoved: false
          }
        ]
      }
    };

    message.receive(OP.LIST_UPDATE_NOTE_JOBS).subscribe(listener);

    message.shortCircuit(
      asReceivedMessage({
        op: OP.LIST_UPDATE_NOTE_JOBS,
        data
      })
    );

    expect(listener).toHaveBeenCalledWith(data);
  });

  it('passes a partial removal payload without noteName', () => {
    const message = new Message();
    const listener = vi.fn();
    const data = {
      noteRunningJobs: {
        jobs: [
          {
            noteId: 'note-1',
            isRemoved: true
          }
        ]
      }
    };

    message.receive(OP.LIST_UPDATE_NOTE_JOBS).subscribe(listener);

    message.shortCircuit(
      asReceivedMessage({
        op: OP.LIST_UPDATE_NOTE_JOBS,
        data
      })
    );

    expect(listener).toHaveBeenCalledWith(data);
  });

  it('filters a non-removal job update without noteName', () => {
    const message = new Message();
    const listener = vi.fn();

    message.receive(OP.LIST_UPDATE_NOTE_JOBS).subscribe(listener);

    message.shortCircuit(
      asReceivedMessage({
        op: OP.LIST_UPDATE_NOTE_JOBS,
        data: {
          noteRunningJobs: {
            jobs: [
              {
                noteId: 'note-1',
                isRemoved: false
              }
            ]
          }
        }
      })
    );

    expect(listener).not.toHaveBeenCalled();
  });

  it('filters a payload without a jobs array', () => {
    const message = new Message();
    const listener = vi.fn();

    message.receive(OP.LIST_UPDATE_NOTE_JOBS).subscribe(listener);

    message.shortCircuit(
      asReceivedMessage({
        op: OP.LIST_UPDATE_NOTE_JOBS,
        data: {
          noteRunningJobs: {}
        }
      })
    );

    expect(listener).not.toHaveBeenCalled();
  });

  it('keeps existing behavior for an OP without a guard', () => {
    const message = new Message();
    const listener = vi.fn();
    const data = {};

    message.receive(OP.NOTE).subscribe(listener);

    message.shortCircuit(
      asReceivedMessage({
        op: OP.NOTE,
        data
      })
    );

    expect(listener).toHaveBeenCalledWith(data);
  });
});
