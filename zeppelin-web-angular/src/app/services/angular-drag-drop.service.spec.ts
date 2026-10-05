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

import * as angular from 'angular';
import { describe, expect, it, vi } from 'vitest';

import { AngularDragDropService } from './angular-drag-drop.service';

type TestScope = angular.IScope & Record<string, unknown>;

interface CallbackService {
  callEventCallback(scope: angular.IScope, callbackName: string, event: DragEvent): void;
}

type DragDropFactory = ($parse: angular.IParseService) => CallbackService;
type AnnotatedDragDropFactory = ['$parse', DragDropFactory];

const createCallbackService = (): CallbackService => {
  let registeredFactory: DragDropFactory | undefined;
  const moduleStub = {
    factory: (_name: string, definition: AnnotatedDragDropFactory) => {
      registeredFactory = definition[1];
      return moduleStub;
    },
    directive: () => {
      return moduleStub;
    }
  };

  new AngularDragDropService().addDragDropDirectives(moduleStub as unknown as angular.IModule);

  const $parse = ((expression: string) => (scope: angular.IScope) => {
    return (scope as TestScope)[expression];
  }) as angular.IParseService;

  if (!registeredFactory) {
    throw new Error('customDragDropService factory was not registered');
  }
  return registeredFactory($parse);
};

describe('AngularDragDropService', () => {
  it('invokes a scope callback with the drag event, UI value, and parsed arguments', () => {
    const callbackService = createCallbackService();
    const callback = vi.fn();
    const argument = { name: 'axis' };
    const scope = { save: callback, axisSpec: argument } as unknown as TestScope;
    const event = {} as DragEvent;

    callbackService.callEventCallback(scope, 'save(axisSpec)', event);

    expect(callback).toHaveBeenCalledWith(event, undefined, argument);
    expect(callback.mock.instances[0]).toBe(scope);
  });

  it('ignores a missing callback', () => {
    const callbackService = createCallbackService();
    const scope = {} as TestScope;

    expect(() => callbackService.callEventCallback(scope, 'missing()', {} as DragEvent)).not.toThrow();
  });
});
