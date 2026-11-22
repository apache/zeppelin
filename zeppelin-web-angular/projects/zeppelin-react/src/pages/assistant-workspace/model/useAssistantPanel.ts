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

import { useEffect, useMemo, useSyncExternalStore } from 'react';
import type { AssistantDataSource } from './assistantDataSource';
import { AssistantPanelModel } from './assistantPanelModel';
import type { AssistantPanel } from './assistantPanelState';

export const useAssistantPanelLifecycle = (model: AssistantPanelModel): void => {
  useEffect(() => {
    model.start();
    return () => model.dispose();
  }, [model]);
};

export const useAssistantPanelSnapshot = (model: AssistantPanelModel): AssistantPanel =>
  useSyncExternalStore(model.subscribe, model.getSnapshot);

export const useAssistantPanel = (source: AssistantDataSource): AssistantPanel => {
  const model = useMemo(() => new AssistantPanelModel(source), [source]);
  useAssistantPanelLifecycle(model);

  return useAssistantPanelSnapshot(model);
};
