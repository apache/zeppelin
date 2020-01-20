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

import {
  Compiler,
  CompilerFactory,
  CompilerOptions,
  COMPILER_OPTIONS,
  StaticProvider,
  ViewEncapsulation
} from '@angular/core';
import { JitCompilerFactory } from '@angular/platform-browser-dynamic';

const compilerOptions: CompilerOptions = {
  useJit: true,
  defaultEncapsulation: ViewEncapsulation.None
};

export function createCompiler(compilerFactory: CompilerFactory) {
  return compilerFactory.createCompiler([compilerOptions]);
}

export const RUNTIME_COMPILER_PROVIDERS: StaticProvider[] = [
  { provide: COMPILER_OPTIONS, useValue: compilerOptions, multi: true },
  { provide: CompilerFactory, useClass: JitCompilerFactory, deps: [COMPILER_OPTIONS] },
  { provide: Compiler, useFactory: createCompiler, deps: [CompilerFactory] }
];

// TODO(hsuanxyz)
// buildOptimizer false
// import 'core-js/es7/reflect';
// https://github.com/angular/angular/issues/27584#issuecomment-446462051
