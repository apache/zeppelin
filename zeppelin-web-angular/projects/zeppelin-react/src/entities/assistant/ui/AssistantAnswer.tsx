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

import { Skeleton } from 'antd';
import type { ReactNode } from 'react';
import { answerText, answerToolCalls } from '../model/messageHistory';
import type { AssistantContentRenderer, AssistantAnswerView, AssistantToolActivity } from './answerView';
import { AssistantAnswerError } from './AssistantAnswerError';
import { AssistantMessageItem } from './AssistantMessageItem';
import { AssistantToolLog, toolLabel } from './AssistantToolLog';
import * as styles from './AssistantAnswer.css';

const Progress = ({ text }: { text: string }) => (
  <div role="status" className={styles.progress}>
    <span className={styles.dots} aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
    {text}
  </div>
);

const progressText = (content: string, tools: AssistantToolActivity[]) => {
  const running = tools.find(tool => tool.status === 'running');
  if (running) return `Running ${toolLabel(running)}…`;

  return content ? 'Writing answer…' : 'Thinking…';
};

export interface AssistantAnswerProps {
  view: AssistantAnswerView;
  /** Called from the error display. */
  onRetry?: () => void;
  renderContent?: AssistantContentRenderer;
}

const renderAnswerState = (
  view: AssistantAnswerView,
  onRetry?: () => void
): { content: string; tools: AssistantToolActivity[]; streaming: boolean; details: ReactNode } => {
  switch (view.status) {
    case 'loading':
      return {
        content: '',
        tools: [],
        streaming: true,
        details: (
          <>
            <Progress text="Thinking…" />
            <Skeleton className={styles.skeleton} active title={false} paragraph={{ rows: 2, width: ['92%', '64%'] }} />
          </>
        )
      };

    case 'streaming': {
      const tools = view.tools ?? [];
      return {
        content: view.content,
        tools,
        streaming: true,
        details: <Progress text={progressText(view.content, tools)} />
      };
    }

    case 'done':
      return {
        content: answerText(view.message),
        tools: answerToolCalls(view.message).map(call => ({ ...call, status: 'done' as const })),
        streaming: false,
        details: null
      };

    case 'error':
      return {
        content: view.content ?? '',
        tools: (view.tools ?? []).map(tool =>
          tool.status === 'running' ? { ...tool, status: 'unknown' as const } : tool
        ),
        streaming: false,
        details: <AssistantAnswerError kind={view.kind} message={view.message} onRetry={onRetry} />
      };
  }
};

export const AssistantAnswer = ({ view, onRetry, renderContent }: AssistantAnswerProps) => {
  const presentation = renderAnswerState(view, onRetry);

  return (
    <AssistantMessageItem
      role="assistant"
      content={presentation.content}
      streaming={presentation.streaming}
      renderContent={renderContent}
    >
      <AssistantToolLog tools={presentation.tools} />
      {presentation.details}
    </AssistantMessageItem>
  );
};
