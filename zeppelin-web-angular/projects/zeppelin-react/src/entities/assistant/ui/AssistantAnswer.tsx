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

/** One assistant answer in whichever state its run is in. Each state replaces the last, so only one shows at a time. */
export const AssistantAnswer = ({ view, onRetry, renderContent }: AssistantAnswerProps) => {
  switch (view.status) {
    case 'loading':
      return (
        <AssistantMessageItem role="assistant" content="" streaming>
          <Progress text="Thinking…" />
          <Skeleton className={styles.skeleton} active title={false} paragraph={{ rows: 2, width: ['92%', '64%'] }} />
        </AssistantMessageItem>
      );
    case 'streaming': {
      const tools = view.tools ?? [];
      return (
        <AssistantMessageItem role="assistant" content={view.content} streaming renderContent={renderContent}>
          <AssistantToolLog tools={tools} />
          <Progress text={progressText(view.content, tools)} />
        </AssistantMessageItem>
      );
    }
    case 'done': {
      const tools = answerToolCalls(view.message).map(call => ({ ...call, status: 'done' as const }));
      return (
        <AssistantMessageItem role="assistant" content={answerText(view.message)} renderContent={renderContent}>
          <AssistantToolLog tools={tools} />
        </AssistantMessageItem>
      );
    }
    case 'error': {
      const tools = (view.tools ?? []).map(tool =>
        tool.status === 'running' ? { ...tool, status: 'unknown' as const } : tool
      );
      return (
        <AssistantMessageItem role="assistant" content={view.content ?? ''} renderContent={renderContent}>
          <AssistantToolLog tools={tools} />
          <AssistantAnswerError kind={view.kind} message={view.message} onRetry={onRetry} />
        </AssistantMessageItem>
      );
    }
  }
};
