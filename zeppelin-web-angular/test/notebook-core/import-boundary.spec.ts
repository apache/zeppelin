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

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { createFixtureHost } from './compiler-fixture';
import {
  sourceRoot,
  zeppelinWebAngularRoot,
  reactNotebookCoreBoundaryFiles,
  forbiddenModulePrefixes,
  forbiddenReactNotebookCoreConsumerModulePrefixes,
  sourceFiles,
  findReactNotebookConsumerViolations,
  findNotebookContractViolations,
  findViolations,
  formatViolations,
  readCompilerOptions
} from './import-boundary';

describe('notebook core import boundary', () => {
  it('resolves the React public contract without exposing source subpaths', () => {
    const path = reactNotebookCoreBoundaryFiles[1];
    const options = readCompilerOptions(resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/tsconfig.json'));

    expect(
      ts.resolveModuleName('@zeppelin/notebook-core', path, options, ts.sys).resolvedModule?.resolvedFileName
    ).toBe(resolve(sourceRoot, 'public-api.ts'));
    expect(
      ts.resolveModuleName('@zeppelin/notebook-core/host-remote-contract', path, options, ts.sys).resolvedModule
    ).toBeUndefined();
  });

  it.each(['http', 'https', 'node:http', 'node:https', 'http2', 'node:http2'])(
    'rejects Node HTTP transport %s in core and consumer',
    specifier => {
      for (const [path, prefixes] of [
        [resolve(sourceRoot, 'host-remote-contract.ts'), forbiddenModulePrefixes],
        [reactNotebookCoreBoundaryFiles[1], forbiddenReactNotebookCoreConsumerModulePrefixes]
      ] as const) {
        expect(findViolations(path, `export * from '${specifier}';`, prefixes)).toEqual([
          `${path}: import ${specifier}`
        ]);
      }
    }
  );

  it('rejects a compiler-valid Node HTTP handle in the snapshot', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const source = `/// <reference types="node" />\nimport type { ClientRequest } from 'node:http';\n${readFileSync(
      path,
      'utf8'
    ).replace('noteId: string;', 'noteId: string; transport?: ClientRequest;')}`;
    const options = readCompilerOptions(resolve(sourceRoot, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[path, source]]));

    expect(ts.getPreEmitDiagnostics(ts.createProgram([path], options, host))).toEqual([]);
    expect(findViolations(path, source)).toEqual([`${path}: reference node`, `${path}: import node:http`]);
  });

  it('rejects a compiler-valid framework type exposed by an external wrapper', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const source = readFileSync(path, 'utf8').replace(
      'noteId: string;',
      "noteId: string; button?: import('ng-zorro-antd/button').NzButtonComponent;"
    );
    const options = readCompilerOptions(resolve(sourceRoot, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[path, source]]));
    expect(ts.getPreEmitDiagnostics(ts.createProgram([path], options, host))).toEqual([]);
    expect(findViolations(path, source)).toEqual([`${path}: import ng-zorro-antd/button`]);
  });

  it('allows only checked local sources and the reviewed tslib dependency in core', () => {
    const path = resolve(sourceRoot, 'public-api.ts');
    expect(findViolations(path, "export * from './host-remote-contract';")).toEqual([]);
    expect(findViolations(path, "export { __assign } from 'tslib';")).toEqual([]);
    expect(findViolations(path, "export * from 'unknown-wrapper';")).toEqual([`${path}: import unknown-wrapper`]);
  });

  it('stays framework-neutral and transport-neutral', () => {
    const violations = sourceFiles(sourceRoot).flatMap(path => {
      const source = readFileSync(path, 'utf8');
      return findViolations(path, source);
    });

    expect(violations).toEqual([]);
  });

  it.each(['import', 'alias', 'reference'])(
    'rejects a compiler-valid declaration outside the scanned core source through %s',
    form => {
      const path = resolve(sourceRoot, 'host-remote-contract.ts');
      const helper = resolve(sourceRoot, '../framework.d.ts');
      const options = {
        ...readCompilerOptions(resolve(sourceRoot, '../tsconfig.json')),
        rootDir: sourceRoot,
        baseUrl: sourceRoot,
        paths: { '@contract-helper': ['../framework.d.ts'] }
      };
      const specifier = form === 'alias' ? '@contract-helper' : '../framework';
      const declaration =
        form === 'reference'
          ? "type AngularHandle = import('@angular/core').Provider;"
          : "export type { Provider as AngularHandle } from '@angular/core';";
      const field = form === 'reference' ? 'AngularHandle' : `import('${specifier}').AngularHandle`;
      const source = `${form === 'reference' ? '/// <reference path="../framework.d.ts" />\n' : ''}${readFileSync(
        path,
        'utf8'
      ).replace('noteId: string;', `noteId: string; framework?: ${field};`)}`;
      const files = new Map([
        [path, source],
        [helper, declaration]
      ]);
      const host = createFixtureHost(options, files);
      const program = ts.createProgram([path], options, host);

      expect(ts.getPreEmitDiagnostics(program)).toEqual([]);
      expect(program.getSourceFile(helper)).toBeDefined();
      expect(findViolations(path, source, forbiddenModulePrefixes, options, host)).toEqual([
        `${path}: ${form === 'reference' ? 'reference ../framework.d.ts' : `import ${specifier}`}`
      ]);
    }
  );

  it('rejects direct transport imports in the React entry point and notebook core contract', () => {
    const violations = reactNotebookCoreBoundaryFiles.flatMap(path => {
      const source = readFileSync(path, 'utf8');
      return findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes);
    });

    expect(formatViolations(violations)).toEqual([]);
  });

  it('checks all production React consumers of the notebook contract', () => {
    expect(findReactNotebookConsumerViolations()).toEqual([]);
  });

  it.each(['direct', 'helper', 'route', 'barrel', 'javascript', 'cycle', 'computed', 'require-outside'])(
    'discovers a new notebook consumer and rejects its %s transport dependency',
    form => {
      const root = resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src');
      const consumer = resolve(root, 'NotebookBoundaryFixture.tsx');
      const helper = resolve(
        root,
        form === 'javascript'
          ? 'transportFixture.js'
          : form === 'require-outside'
            ? '../transportFixture.ts'
            : 'transportFixture.ts'
      );
      const route = resolve(root, 'NotebookRouteFixture.tsx');
      const barrel = resolve(root, 'contractFixture.ts');
      const transport = "export { webSocket } from 'rxjs/webSocket';";
      const files = new Map<string, string>([
        [
          consumer,
          `import type { NotebookCoreRemoteProps } from '${form === 'barrel' ? './contractFixture' : './notebookCoreContract'}';
          export const read = (props: NotebookCoreRemoteProps) => props.core.getSnapshot();
          ${['helper', 'javascript', 'cycle'].includes(form) ? "export * from '@/transportFixture';" : ''}
          ${form === 'direct' || form === 'barrel' ? transport : ''}
          ${form === 'require-outside' ? "export const transport = require('../transportFixture');" : ''}
          ${form === 'computed' ? "export const load = () => import('rxjs/' + 'webSocket');" : ''}`
        ],
        [helper, `${transport} ${form === 'cycle' ? "export { read } from './NotebookBoundaryFixture';" : ''}`],
        [route, `export { read } from './NotebookBoundaryFixture'; ${form === 'route' ? transport : ''}`],
        [barrel, "export type { NotebookCoreRemoteProps } from './notebookCoreContract';"]
      ]);
      const options = readCompilerOptions(resolve(root, '../tsconfig.json'));
      const host = createFixtureHost(options, files);
      const roots = [consumer, route, barrel, ...reactNotebookCoreBoundaryFiles];
      expect(ts.getPreEmitDiagnostics(ts.createProgram(roots, options, host))).toEqual([]);
      const violations = findReactNotebookConsumerViolations(roots, options, host);
      const offender = ['helper', 'javascript', 'cycle', 'require-outside'].includes(form)
        ? helper
        : form === 'route'
          ? route
          : consumer;
      expect(violations).toContain(
        `${offender}: import ${form === 'computed' ? '<computed module dependency>' : 'rxjs/webSocket'}`
      );
    }
  );

  it.each([
    ["export type { Message } from '@zeppelin/sdk';", 'import @zeppelin/sdk'],
    ["export { request } from 'node:http';", 'import node:http'],
    ["export type { ClientHttp2Session } from 'node:http2';", 'import node:http2'],
    ['export const request = fetch;', 'global fetch'],
    ['export const socket = WebSocket;', 'global WebSocket']
  ])('rejects transport access in a consumer of the public core API: %s', (transport, violation) => {
    const root = resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src');
    const consumer = resolve(root, 'NotebookBoundaryFixture.tsx');
    const source = `import type { NotebookCoreRemoteProps } from '@zeppelin/notebook-core';
      export const read = (props: NotebookCoreRemoteProps) => props.core.getSnapshot(); ${transport}`;
    const options = readCompilerOptions(resolve(root, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[consumer, source]]));
    expect(ts.getPreEmitDiagnostics(ts.createProgram([consumer], options, host))).toEqual([]);
    expect(findReactNotebookConsumerViolations([consumer], options, host)).toContain(`${consumer}: ${violation}`);
  });

  it('rejects the mixed public aggregator as an internal notebook consumer dependency', () => {
    const root = resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src');
    const consumer = resolve(root, 'NotebookBoundaryFixture.tsx');
    const source = "export type { NotebookCoreRemoteProps } from './main';";
    const options = readCompilerOptions(resolve(root, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[consumer, source]]));
    expect(findReactNotebookConsumerViolations([consumer], options, host)).toContain(
      `${consumer}: notebook consumer must use the contract bridge instead of the public aggregator`
    );
  });

  it('allows a neutral notebook consumer without traversing unrelated legacy page exports', () => {
    const root = resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src');
    const consumer = resolve(root, 'NotebookBoundaryFixture.tsx');
    const source =
      "import type { NotebookCoreRemoteProps } from './notebookCoreContract'; export const read = (props: NotebookCoreRemoteProps) => props.core.getSnapshot();";
    const options = readCompilerOptions(resolve(root, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[consumer, source]]));
    expect(findReactNotebookConsumerViolations([...reactNotebookCoreBoundaryFiles, consumer], options, host)).toEqual(
      []
    );
  });

  it('keeps the React contract dependent only on the public core entry point', () => {
    const path = reactNotebookCoreBoundaryFiles[1];
    expect(findNotebookContractViolations(path, readFileSync(path, 'utf8'))).toEqual([]);
  });

  it('rejects a compiler-valid transport re-export through a local helper', () => {
    const path = reactNotebookCoreBoundaryFiles[1];
    const helper = resolve(dirname(path), 'notebookTransport.ts');
    const source = `${readFileSync(path, 'utf8')}\nexport { webSocket } from './notebookTransport';`;
    const files = new Map([
      [path, source],
      [helper, "export { webSocket } from 'rxjs/webSocket';"]
    ]);
    const options = readCompilerOptions(resolve(dirname(path), '../tsconfig.json'));
    const host = createFixtureHost(options, files);

    expect(ts.getPreEmitDiagnostics(ts.createProgram([path], options, host))).toEqual([]);
    expect(findNotebookContractViolations(path, source)).toEqual([
      `${path}: notebook contract dependency ./notebookTransport`
    ]);
  });

  it.each(["import(('rxjs/webSocket'))", "import('rxjs/' + 'webSocket')"])(
    'rejects wrapped or computed dynamic dependencies: %s',
    expression => {
      const source = `export const load = () => ${expression};`;
      expect(findNotebookContractViolations(reactNotebookCoreBoundaryFiles[1], source).length).toBeGreaterThan(0);
      expect(findViolations(resolve(sourceRoot, 'public-api.ts'), source).length).toBeGreaterThan(0);
    }
  );

  it.each([
    "import type { Port } from './helper';",
    "export type { Port } from '@/helper';",
    "export type Port = import('./helper').Port;",
    "export const load = () => import('./helper');"
  ])('rejects other dependency forms in the type-only contract: %s', source => {
    expect(findNotebookContractViolations(reactNotebookCoreBoundaryFiles[1], source)).toHaveLength(1);
  });

  it.each(['import(`rxjs/webSocket`)', "import('rxjs/webSocket', {})"])(
    'rejects dynamic transport syntax %s',
    expression => {
      const path = reactNotebookCoreBoundaryFiles[1];
      expect(
        findViolations(
          path,
          `export const load = () => ${expression};`,
          forbiddenReactNotebookCoreConsumerModulePrefixes
        )
      ).toEqual([`${path}: import rxjs/webSocket`]);
    }
  );

  it.each([
    ['path', '../../zeppelin-react/node_modules/@types/react/index.d.ts'],
    ['types', 'react'],
    ['types', 'ws']
  ])('rejects forbidden reference %s=%s', (kind, target) => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    expect(findViolations(path, `/// <reference ${kind}="${target}" />`)).toEqual([`${path}: reference ${target}`]);
  });

  it.each(['dom', 'dom.iterable', 'webworker', 'webworker.importscripts'])(
    'rejects browser library reference %s',
    lib => {
      expect(findViolations('library.ts', `/// <reference lib="${lib}" />`)).toEqual([`library.ts: library ${lib}`]);
    }
  );

  it.each(['globalThis', 'window', 'self'])('rejects indexed transport types on %s', owner => {
    expect(findViolations('indexed.ts', `export type Socket = InstanceType<(typeof ${owner})["WebSocket"]>;`)).toEqual([
      'indexed.ts: global WebSocket'
    ]);
    expect(findViolations('indexed.ts', `export type Request = (typeof ${owner})['fetch'];`)).toEqual([
      'indexed.ts: global fetch'
    ]);
  });

  it('rejects template-literal global transport access', () => {
    expect(findViolations('template.ts', 'export const request = window[`fetch`];')).toEqual([
      'template.ts: global fetch'
    ]);
  });

  it('allows standard libraries and local indexed properties', () => {
    expect(findViolations('local.ts', '/// <reference lib="es2020" />')).toEqual([]);
    expect(findViolations('local.ts', 'type Local = { fetch: string }; export type Value = Local["fetch"];')).toEqual(
      []
    );
    expect(findViolations('local.ts', 'export type Parse = (typeof globalThis)["parseInt"];')).toEqual([]);
  });

  it('rejects a compiler-valid WebSocket leak in the actual snapshot contract', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const source = `/// <reference lib="dom" />\n${readFileSync(path, 'utf8').replace(
      'noteId: string;',
      'noteId: string; socket?: InstanceType<(typeof globalThis)["WebSocket"]>;'
    )}`;
    const options = readCompilerOptions(resolve(sourceRoot, '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[path, source]]));
    const program = ts.createProgram([path], options, host);

    expect(ts.getPreEmitDiagnostics(program)).toEqual([]);
    expect(findViolations(path, source)).toEqual([`${path}: library dom`, `${path}: global WebSocket`]);
  });

  it.each([
    "export type Leak = import('./host-remote-contract.spec').AngularHandle;",
    "export * from './host-remote-contract.spec';",
    "export const load = () => import('./host-remote-contract.spec');"
  ])('rejects a production dependency on an excluded spec: %s', source => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    expect(findViolations(path, source)).toEqual([`${path}: import ./host-remote-contract.spec`]);
  });

  it('rejects an alias and reference directive resolving to an excluded spec', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const options = {
      ...readCompilerOptions(resolve(sourceRoot, '../tsconfig.json')),
      baseUrl: sourceRoot,
      paths: { '@test-contract': ['host-remote-contract.spec.ts'] }
    };
    expect(findViolations(path, "export * from '@test-contract';", forbiddenModulePrefixes, options)).toEqual([
      `${path}: import @test-contract`
    ]);
    expect(findViolations(path, '/// <reference path="./host-remote-contract.spec.ts" />')).toEqual([
      `${path}: reference ./host-remote-contract.spec.ts`
    ]);
    expect(findViolations(path, '/// <reference types="./host-remote-contract.spec.ts" />')).toEqual([
      `${path}: reference ./host-remote-contract.spec.ts`
    ]);
  });

  it('rejects a compiler-valid framework leak through an imported spec', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const spec = resolve(sourceRoot, 'host-remote-contract.spec.ts');
    const source = readFileSync(path, 'utf8').replace(
      'noteId: string;',
      "noteId: string; framework?: import('./host-remote-contract.spec').AngularHandle;"
    );
    const files = new Map([
      [path, source],
      [spec, `${readFileSync(spec, 'utf8')}\nexport type { Provider as AngularHandle } from '@angular/core';`]
    ]);
    const options = readCompilerOptions(resolve(sourceRoot, '../tsconfig.json'));
    const host = createFixtureHost(options, files);
    const program = ts.createProgram([resolve(sourceRoot, 'public-api.ts')], options, host);

    expect(ts.getPreEmitDiagnostics(program)).toEqual([]);
    expect(program.getSourceFile(spec)).toBeDefined();
    expect(findViolations(path, source)).toEqual([`${path}: import ./host-remote-contract.spec`]);
  });

  it('allows a local reference and a core dynamic import', () => {
    const path = resolve(sourceRoot, 'public-api.ts');
    expect(findViolations(path, '/// <reference path="./host-remote-contract.ts" />')).toEqual([]);
    expect(
      findViolations(
        reactNotebookCoreBoundaryFiles[1],
        'export const load = () => import(`@zeppelin/notebook-core`);',
        forbiddenReactNotebookCoreConsumerModulePrefixes
      )
    ).toEqual([]);
  });

  it.each(['ws', '@types/ws', 'isomorphic-ws', 'sockjs-client', '@types/sockjs-client'])(
    'rejects WebSocket implementation %s in core and consumer',
    specifier => {
      for (const [path, prefixes] of [
        [resolve(sourceRoot, 'host-remote-contract.ts'), forbiddenModulePrefixes],
        [reactNotebookCoreBoundaryFiles[1], forbiddenReactNotebookCoreConsumerModulePrefixes]
      ] as const) {
        expect(findViolations(path, `export * from '${specifier}';`, prefixes)).toEqual([
          `${path}: import ${specifier}`
        ]);
      }
    }
  );

  it('rejects worker-global transport access', () => {
    expect(
      findViolations(
        reactNotebookCoreBoundaryFiles[1],
        'export const request = self.fetch;',
        forbiddenReactNotebookCoreConsumerModulePrefixes
      )
    ).toEqual([`${reactNotebookCoreBoundaryFiles[1]}: global fetch`]);
  });

  it.each([
    ['const browser = window; export const request = browser.fetch;', 'window'],
    ['const browser = (globalThis); export const request = browser.fetch;', 'globalThis'],
    ['let browser: Window; browser = window; export const request = browser.fetch;', 'window'],
    ['const browser = self as typeof self; export const socket = browser.WebSocket;', 'self']
  ])('rejects capturing a transport global in a local alias: %s', (source, owner) => {
    const path = reactNotebookCoreBoundaryFiles[1];
    const options = readCompilerOptions(resolve(dirname(path), '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[path, source]]));
    expect(ts.getPreEmitDiagnostics(ts.createProgram([path], options, host))).toEqual([]);
    expect(findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes)).toContain(
      `${path}: global object alias ${owner}`
    );
  });

  it.each([
    ['export const request = (window).fetch;', 'global fetch'],
    ['export const request = (window as Window).fetch;', 'global fetch'],
    ['export const request = (globalThis)["fetch"];', 'global fetch'],
    ['export const request = (self as typeof self)["fetch"];', 'global fetch'],
    ['const { fetch } = window; export const request = fetch;', 'global fetch'],
    ['const { fetch: request } = (window); export { request };', 'global fetch'],
    ['let fetch: typeof window.fetch; ({ fetch } = window); export const request = fetch;', 'global fetch'],
    ['export const browser = { fetch };', 'global fetch'],
    ['type fetch = string; export const request = fetch;', 'global fetch']
  ])('rejects wrapped transport globals: %s', (source, violation) => {
    const path = reactNotebookCoreBoundaryFiles[1];
    const options = readCompilerOptions(resolve(dirname(path), '../tsconfig.json'));
    const host = createFixtureHost(options, new Map([[path, source]]));
    expect(ts.getPreEmitDiagnostics(ts.createProgram([path], options, host))).toEqual([]);
    expect(findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes)).toContain(
      `${path}: ${violation}`
    );
  });

  it.each([
    "export const fetch = () => 'cached'; export const result = fetch();",
    'export const read = (fetch: () => string) => fetch();',
    "const window = { fetch: () => 'cached' }; const browser = window; export const result = (window).fetch() + browser.fetch();",
    'export function read({ fetch }: { fetch: () => string }) { return fetch(); }',
    "const fetch = () => 'cached'; export const local = { fetch };",
    "const local = { fetch: () => 'cached' }; const { fetch: read } = local; export const result = read();"
  ])('allows lexical local bindings with transport-like names: %s', source => {
    expect(
      findViolations(reactNotebookCoreBoundaryFiles[1], source, forbiddenReactNotebookCoreConsumerModulePrefixes)
    ).toEqual([]);
  });

  it('does not let a nested local binding hide an outer transport global', () => {
    const source = 'export function read(fetch: () => string) { return fetch(); } export const request = fetch;';
    const path = reactNotebookCoreBoundaryFiles[1];
    expect(findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes)).toEqual([
      `${path}: global fetch`
    ]);
  });

  it('allows direct non-transport browser properties and ordinary local objects', () => {
    const source = 'const browser = { innerWidth: 42 }; export const width = browser.innerWidth + window.innerWidth;';
    expect(
      findViolations(reactNotebookCoreBoundaryFiles[1], source, forbiddenReactNotebookCoreConsumerModulePrefixes)
    ).toEqual([]);
  });

  it('ignores forbidden words in comments and string values', () => {
    const source = `// React may render this later.\nexport const note = 'fetch over WebSocket';`;

    expect(findViolations('comment-fixture.ts', source)).toEqual([]);
  });

  it.each([
    '../../zeppelin-sdk/src/public-api',
    '../../zeppelin-react/node_modules/@types/react',
    '../../../node_modules/rxjs/dist/types/index',
    '@zeppelin/services/interpreter.service'
  ])('rejects forbidden dependencies through %s', moduleSpecifier => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const source = `export type { Leaked } from '${moduleSpecifier}';`;

    expect(findViolations(path, source)).toEqual([`${path}: import ${moduleSpecifier}`]);
  });

  it('rejects a compiler alias to the SDK even when its spelling is neutral', () => {
    const path = resolve(sourceRoot, 'host-remote-contract.ts');
    const options = {
      ...readCompilerOptions(resolve(sourceRoot, '../tsconfig.json')),
      baseUrl: zeppelinWebAngularRoot,
      paths: { '@mount-data': ['projects/zeppelin-sdk/src/public-api.ts'] }
    };

    expect(
      findViolations(path, "export type { Message } from '@mount-data';", forbiddenModulePrefixes, options)
    ).toEqual([`${path}: import @mount-data`]);
  });

  it('rejects a relative SDK import from the React contract consumer', () => {
    const path = reactNotebookCoreBoundaryFiles[1];
    const source = `export type { Message } from '../../zeppelin-sdk/src/public-api';`;

    expect(findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes)).toEqual([
      `${path}: import ../../zeppelin-sdk/src/public-api`
    ]);
  });

  it.each([
    '../../../src/app/services/message.service',
    '../../../src/app/services/notebook.service',
    '../../../src/app/services/base-rest',
    '../../../node_modules/rxjs/dist/types/webSocket/index'
  ])('rejects direct shell and WebSocket transport imports through %s', moduleSpecifier => {
    const path = reactNotebookCoreBoundaryFiles[1];

    expect(
      findViolations(path, `export * from '${moduleSpecifier}';`, forbiddenReactNotebookCoreConsumerModulePrefixes)
    ).toEqual([`${path}: import ${moduleSpecifier}`]);
  });

  it('rejects transport types and computed global transport access', () => {
    const source = [
      'export type Socket = WebSocket;',
      'export type Request = XMLHttpRequest;',
      "export const request = globalThis['fetch'];",
      "export const socket = new window['WebSocket']('/ws');"
    ].join('\n');

    expect(findViolations('transport-types.ts', source)).toEqual([
      'transport-types.ts: global WebSocket',
      'transport-types.ts: global XMLHttpRequest',
      'transport-types.ts: global fetch',
      'transport-types.ts: global WebSocket'
    ]);
  });

  it('allows transport-like property names without permitting transport types', () => {
    const source = [
      'type Port = { fetch: () => string; WebSocket(): string };',
      'interface OtherPort { XMLHttpRequest: string; fetch(): string }',
      'const port = { fetch: () => "local" }; port.fetch();'
    ].join('\n');

    expect(findViolations('property-names.ts', source)).toEqual([]);
  });

  it('allows local contract re-exports and the React shared-core type import', () => {
    const path = resolve(sourceRoot, 'public-api.ts');

    expect(findViolations(path, "export * from './host-remote-contract';")).toEqual([]);
    expect(
      findViolations(
        reactNotebookCoreBoundaryFiles[1],
        "export type { NotebookCoreRemoteProps } from '@zeppelin/notebook-core';",
        forbiddenReactNotebookCoreConsumerModulePrefixes
      )
    ).toEqual([]);
  });

  it('rejects static, dynamic and direct transport dependencies', () => {
    const source = [
      `import type { OP } from '@zeppelin/sdk';`,
      `export { useMemo } from 'react';`,
      `type LeakedMessage = import('@zeppelin/sdk').Message;`,
      `import { createRoot } from 'react-dom/client';`,
      `import { Provider } from 'react-redux';`,
      `const router = () => import('react-router-dom');`,
      `const load = () => import('rxjs/operators');`,
      `const request = () => fetch('/api/notebook');`,
      `const socket = new globalThis.WebSocket('/ws');`,
      `const xhr = new window.XMLHttpRequest();`
    ].join('\n');

    expect(findViolations('violation-fixture.ts', source)).toEqual([
      'violation-fixture.ts: import @zeppelin/sdk',
      'violation-fixture.ts: import react',
      'violation-fixture.ts: import @zeppelin/sdk',
      'violation-fixture.ts: import react-dom/client',
      'violation-fixture.ts: import react-redux',
      'violation-fixture.ts: import react-router-dom',
      'violation-fixture.ts: import rxjs/operators',
      'violation-fixture.ts: global fetch',
      'violation-fixture.ts: global WebSocket',
      'violation-fixture.ts: global XMLHttpRequest'
    ]);
  });
});
