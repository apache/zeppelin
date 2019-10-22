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

// TODO
// buildOptimizer false
// import 'core-js/es7/reflect';
// https://github.com/angular/angular/issues/27584#issuecomment-446462051
