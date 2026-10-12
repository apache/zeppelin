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

import { CheckOutlined } from '@ant-design/icons';
import { Spin } from 'antd';
import type { AssistantToolActivity } from './answerView';
import * as styles from './AssistantAnswer.css';

export const toolLabel = (tool: Pick<AssistantToolActivity, 'name'>) => tool.name || 'Tool';

export interface AssistantToolLogProps {
  tools: AssistantToolActivity[];
}

/** The tools behind an answer. Open while one is still running, collapsed once they have all finished. */
export const AssistantToolLog = ({ tools }: AssistantToolLogProps) => {
  if (!tools.length) return null;
  const running = tools.some(tool => tool.status === 'running');
  const count = `${tools.length} ${tools.length === 1 ? 'tool' : 'tools'}`;
  return (
    <details className={styles.toolLog} open={running}>
      <summary className={styles.toolSummary}>{running ? `Using ${count}` : `Used ${count}`}</summary>
      <ul className={styles.toolList}>
        {tools.map(tool => (
          <li key={tool.id} className={styles.toolItem} data-status={tool.status}>
            {tool.status === 'running' ? (
              <span className={styles.toolIcon} role="img" aria-label="Running">
                <Spin size="small" />
              </span>
            ) : (
              <CheckOutlined className={`${styles.toolIcon} ${styles.toolDone}`} aria-label="Done" />
            )}
            <span className={styles.toolName}>{toolLabel(tool)}</span>
          </li>
        ))}
      </ul>
    </details>
  );
};
