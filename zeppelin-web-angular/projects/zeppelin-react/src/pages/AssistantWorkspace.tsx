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

import { createRoot, Root } from 'react-dom/client';
import type { AssistantShellProps } from '@zeppelin/sdk';
import { ReactErrorBoundary } from '@/components/paragraph/ReactErrorBoundary';
import { ZeppelinThemeProvider } from '@/theme/ZeppelinThemeProvider';
import { ASSISTANT_PREFIX_CLS, ASSISTANT_SEED_TOKENS } from '@/shared/ui/assistant-theme/AssistantScope';
import { AssistantWorkspaceShell } from './assistant-workspace/ui/AssistantWorkspaceShell';

export type AssistantWorkspaceProps = AssistantShellProps;

export const AssistantWorkspace = (props: AssistantWorkspaceProps) => {
  const session = JSON.stringify([props.noteId, props.connection.sessionKey]);
  return <AssistantWorkspaceShell key={session} {...props} />;
};

export const mount = (element: HTMLElement, initialProps: AssistantWorkspaceProps) => {
  const root: Root = createRoot(element);
  const renderWith = (props: AssistantWorkspaceProps) => {
    root.render(
      <ReactErrorBoundary onError={props.onError}>
        <ZeppelinThemeProvider prefixCls={ASSISTANT_PREFIX_CLS} token={ASSISTANT_SEED_TOKENS}>
          <AssistantWorkspace {...props} />
        </ZeppelinThemeProvider>
      </ReactErrorBoundary>
    );
  };

  renderWith(initialProps);
  return {
    update: renderWith,
    unmount: () => root.unmount()
  };
};
