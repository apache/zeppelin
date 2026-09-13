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

import { readdirSync, statSync } from 'node:fs';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';

export const zeppelinWebAngularRoot = resolve(fileURLToPath(new URL('../../', import.meta.url)));
export const sourceRoot = fileURLToPath(new URL('../../projects/zeppelin-notebook-core/src/', import.meta.url));
export const reactNotebookCoreBoundaryFiles = [
  resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src/main.ts'),
  resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src/notebookCoreContract.ts')
];
const forbiddenWebSocketModulePrefixes = ['ws', '@types/ws', 'isomorphic-ws', 'sockjs-client', '@types/sockjs-client'];
const forbiddenNodeHttpModulePrefixes = ['http', 'https', 'http2', 'node:http', 'node:https', 'node:http2'];
export const forbiddenModulePrefixes = [
  ...forbiddenWebSocketModulePrefixes,
  ...forbiddenNodeHttpModulePrefixes,
  '@angular/',
  '@zeppelin/',
  '@types/react',
  '@types/react-dom',
  'react',
  'react-dom',
  'react-redux',
  'react-router',
  'react-router-dom',
  'rxjs',
  'axios'
];
export const forbiddenReactNotebookCoreConsumerModulePrefixes = [
  ...forbiddenWebSocketModulePrefixes,
  ...forbiddenNodeHttpModulePrefixes,
  '@angular/common/http',
  '@zeppelin/sdk',
  '@zeppelin/src/',
  '@zeppelin/services/',
  'axios',
  'rxjs'
];
const forbiddenGlobals = new Set(['fetch', 'WebSocket', 'XMLHttpRequest']);
const transportGlobalOwners = new Set(['globalThis', 'window', 'self']);

export const sourceFiles = (dir: string, accepts = isCheckedSourceFile): string[] =>
  readdirSync(dir).flatMap(entry => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) {
      return sourceFiles(path, accepts);
    }
    return accepts(path) ? [path] : [];
  });

const checkedSourceExtensions = ['.ts', '.tsx', '.mts', '.cts'];

const isSpecSourceFile = (path: string): boolean => /\.spec\.[cm]?[jt]sx?$/.test(path);

const isCheckedSourceFile = (path: string): boolean => {
  return checkedSourceExtensions.some(extension => path.endsWith(extension)) && !isSpecSourceFile(path);
};

export const findReactNotebookConsumerViolations = (
  roots: string[] = sourceFiles(
    resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/src'),
    path => /\.[cm]?[jt]sx?$/.test(path) && !isSpecSourceFile(path)
  ),
  options = readCompilerOptions(resolve(zeppelinWebAngularRoot, 'projects/zeppelin-react/tsconfig.json')),
  host: ts.CompilerHost = ts.createCompilerHost(options)
): string[] => {
  const program = ts.createProgram(roots, options, host);
  const modules = new Map<string, { source: string; dependencies: Set<string> }>();
  const pending = [...program.getSourceFiles()];
  for (const file of pending) {
    if (modules.has(file.fileName) || file.fileName.includes('/node_modules/')) {
      continue;
    }
    const dependencies = new Set<string>();
    const visit = (node: ts.Node): void => {
      const specifier = getModuleSpecifier(node);
      if (specifier) {
        const target = ts.resolveModuleName(specifier, file.fileName, options, host).resolvedModule?.resolvedFileName;
        if (target) {
          dependencies.add(target);
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(file);
    for (const reference of file.referencedFiles) {
      dependencies.add(resolve(dirname(file.fileName), reference.fileName));
    }
    for (const reference of file.typeReferenceDirectives) {
      const target = ts.resolveTypeReferenceDirective(reference.fileName, file.fileName, options, host)
        .resolvedTypeReferenceDirective?.resolvedFileName;
      if (target) {
        dependencies.add(target);
      }
    }
    modules.set(file.fileName, { source: file.text, dependencies });
    // TypeScript can resolve require() without adding its target to the program.
    // Inspect those local sources too, including helpers outside the root list.
    for (const target of dependencies) {
      if (!modules.has(target) && !target.includes('/node_modules/')) {
        const source = host.readFile(target);
        if (source !== undefined) {
          pending.push(ts.createSourceFile(target, source, ts.ScriptTarget.Latest, true, getScriptKind(target)));
        }
      }
    }
  }

  // Discover adapters, re-export barrels and routes from their dependency on the
  // shared contract. A new consumer must not require editing a scanner file list.
  const consumers = new Set([
    ...reactNotebookCoreBoundaryFiles,
    ...[...modules.keys()].filter(path => path.startsWith(sourceRoot))
  ]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const [path, module] of modules) {
      if (!consumers.has(path) && [...module.dependencies].some(target => consumers.has(target))) {
        consumers.add(path);
        changed = true;
      }
    }
  }

  const violations: string[] = [];
  const checked = new Set<string>();
  const main = reactNotebookCoreBoundaryFiles[0];
  const check = (path: string): void => {
    if (checked.has(path) || path.startsWith(sourceRoot)) {
      return;
    }
    checked.add(path);
    const module = modules.get(path);
    if (!module) {
      if (!path.includes('/node_modules/')) {
        violations.push(`${path}: cannot inspect local notebook dependency`);
      }
      return;
    }
    violations.push(
      ...findViolations(path, module.source, forbiddenReactNotebookCoreConsumerModulePrefixes, options, host)
    );
    // The public aggregator also exports existing SDK-backed pages. Check its
    // own imports, but do not include those unrelated pages in the core boundary.
    if (path === main) {
      return;
    }
    for (const target of module.dependencies) {
      if (target === main) {
        violations.push(`${path}: notebook consumer must use the contract bridge instead of the public aggregator`);
      } else {
        check(target);
      }
    }
  };
  consumers.forEach(check);
  return violations;
};

// This type-only bridge has one dependency. Rejecting other imports also prevents
// local helpers from hiding transport re-exports without restricting existing pages.
export const findNotebookContractViolations = (path: string, source: string): string[] => {
  const violations = findViolations(path, source, forbiddenReactNotebookCoreConsumerModulePrefixes);
  const sourceFile = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, getScriptKind(path));
  const visit = (node: ts.Node): void => {
    const specifier = getModuleSpecifier(node);
    if (specifier && specifier !== '@zeppelin/notebook-core') {
      violations.push(`${path}: notebook contract dependency ${specifier}`);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return violations;
};

export const findViolations = (
  path: string,
  source: string,
  forbiddenPrefixes: readonly string[] = forbiddenModulePrefixes,
  compilerOptions = readCompilerOptions(
    resolve(
      zeppelinWebAngularRoot,
      'projects',
      path.includes('zeppelin-react') ? 'zeppelin-react' : 'zeppelin-notebook-core',
      'tsconfig.json'
    )
  ),
  resolutionHost: ts.ModuleResolutionHost = ts.sys
): string[] => {
  const sourceFile = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, getScriptKind(path));
  const violations: string[] = [];

  // Bind lexical declarations without loading libraries or following imports.
  // Import provenance is checked separately; unresolved reserved names here
  // represent globals, while parameters, imports and local declarations do not.
  let bindingChecker: ts.TypeChecker | undefined;
  const isGlobalIdentifier = (node: ts.Identifier): boolean => {
    if (!bindingChecker) {
      const options: ts.CompilerOptions = { noLib: true, noResolve: true, types: [], allowJs: true };
      const host = ts.createCompilerHost(options);
      host.getSourceFile = file => (file === path ? sourceFile : undefined);
      bindingChecker = ts.createProgram([path], options, host).getTypeChecker();
    }
    const symbol = ts.isShorthandPropertyAssignment(node.parent)
      ? bindingChecker.getShorthandAssignmentValueSymbol(node.parent)
      : bindingChecker.getSymbolAtLocation(node);
    return !symbol?.declarations?.length;
  };

  // Only tslib is a reviewed external dependency of this scaffold. Resolve it
  // without project aliases so a package name cannot disguise another target.
  const tslibFile = ts.resolveModuleName(
    'tslib',
    path,
    {
      ...compilerOptions,
      baseUrl: undefined,
      paths: {}
    },
    resolutionHost
  ).resolvedModule?.resolvedFileName;
  const isForbidden = (identities: readonly string[]): boolean => {
    if (path.startsWith(sourceRoot)) {
      const target = identities.filter(isAbsolute).pop();
      const checkedLocalSource =
        target !== undefined &&
        target.startsWith(sourceRoot) &&
        !target.includes('/node_modules/') &&
        isCheckedSourceFile(target) &&
        resolutionHost.fileExists(target);
      if (!checkedLocalSource && !(target !== undefined && target === tslibFile)) {
        return true;
      }
    }
    return identities.some(
      identity =>
        identity === '<computed module dependency>' ||
        isSpecSourceFile(identity) ||
        (!matchesModulePrefix(identity, '@zeppelin/notebook-core') &&
          forbiddenPrefixes.some(prefix => matchesModulePrefix(identity, prefix)))
    );
  };

  // Reference directives are source-file metadata, not import AST nodes.
  for (const reference of sourceFile.libReferenceDirectives) {
    if (/^(?:dom|webworker)(?:\.|$)/i.test(reference.fileName)) {
      violations.push(`${path}: library ${reference.fileName}`);
    }
  }
  for (const reference of sourceFile.referencedFiles) {
    const target = resolve(dirname(path), reference.fileName);
    if (isForbidden(moduleIdentities(target, path, compilerOptions, resolutionHost))) {
      violations.push(`${path}: reference ${reference.fileName}`);
    }
  }
  for (const reference of sourceFile.typeReferenceDirectives) {
    const resolved = ts.resolveTypeReferenceDirective(
      reference.fileName,
      path,
      compilerOptions,
      resolutionHost
    ).resolvedTypeReferenceDirective;
    const identities = [reference.fileName];
    if (resolved?.resolvedFileName) {
      identities.push(...moduleIdentities(resolved.resolvedFileName, path, compilerOptions, resolutionHost));
    }
    if (isForbidden(identities)) {
      violations.push(`${path}: reference ${reference.fileName}`);
    }
  }

  const visit = (node: ts.Node): void => {
    const moduleSpecifier = getModuleSpecifier(node);
    if (moduleSpecifier && isForbidden(moduleIdentities(moduleSpecifier, path, compilerOptions, resolutionHost))) {
      violations.push(`${path}: import ${moduleSpecifier}`);
    }

    const forbiddenGlobal = getForbiddenTransportGlobalName(node, isGlobalIdentifier);
    if (forbiddenGlobal) {
      violations.push(`${path}: global ${forbiddenGlobal}`);
    }
    const globalAlias = getGlobalObjectAlias(node, isGlobalIdentifier);
    if (globalAlias) {
      violations.push(`${path}: global object alias ${globalAlias}`);
    }

    ts.forEachChild(node, visit);
  };

  visit(sourceFile);
  return violations;
};

export const formatViolations = (violations: readonly string[]): string[] => {
  return violations.map(violation => violation.replace(`${zeppelinWebAngularRoot}/`, ''));
};

const getScriptKind = (path: string): ts.ScriptKind => {
  if (path.endsWith('.tsx')) {
    return ts.ScriptKind.TSX;
  }
  if (path.endsWith('.jsx')) {
    return ts.ScriptKind.JSX;
  }
  if (/\.[cm]?js$/.test(path)) {
    return ts.ScriptKind.JS;
  }
  return ts.ScriptKind.TS;
};

const getModuleSpecifier = (node: ts.Node): string | null => {
  if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier) {
    return ts.isStringLiteral(node.moduleSpecifier) ? node.moduleSpecifier.text : null;
  }
  if (ts.isImportTypeNode(node) && ts.isLiteralTypeNode(node.argument) && ts.isStringLiteral(node.argument.literal)) {
    return node.argument.literal.text;
  }
  if (ts.isImportEqualsDeclaration(node) && ts.isExternalModuleReference(node.moduleReference)) {
    const expression = node.moduleReference.expression;
    return expression && ts.isStringLiteral(expression) ? expression.text : null;
  }
  if (
    ts.isCallExpression(node) &&
    node.arguments.length >= 1 &&
    (node.expression.kind === ts.SyntaxKind.ImportKeyword ||
      (ts.isIdentifier(node.expression) && node.expression.text === 'require'))
  ) {
    let argument = node.arguments[0];
    while (ts.isParenthesizedExpression(argument)) {
      argument = argument.expression;
    }
    // Restricted core/bridge dependencies must be statically identifiable.
    return ts.isStringLiteral(argument) || ts.isNoSubstitutionTemplateLiteral(argument)
      ? argument.text
      : '<computed module dependency>';
  }
  return null;
};

export const readCompilerOptions = (path: string): ts.CompilerOptions => {
  const config = ts.readConfigFile(path, ts.sys.readFile);
  if (config.error) {
    throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, '\n'));
  }
  const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, dirname(path));
  if (parsed.errors.length > 0) {
    throw new Error(
      ts.formatDiagnostics(parsed.errors, {
        getCanonicalFileName: file => file,
        getCurrentDirectory: () => zeppelinWebAngularRoot,
        getNewLine: () => '\n'
      })
    );
  }
  return parsed.options;
};

// Resolve aliases and relative paths before comparing package identities. Checking
// the import spelling alone lets the same forbidden dependency cross the boundary.
const moduleIdentities = (
  specifier: string,
  path: string,
  compilerOptions: ts.CompilerOptions,
  resolutionHost: ts.ModuleResolutionHost = ts.sys
): string[] => {
  const identities = [specifier];
  const resolved = ts.resolveModuleName(specifier, path, compilerOptions, resolutionHost).resolvedModule;
  if (!resolved) {
    return identities;
  }
  if (resolved.packageId) {
    identities.push(resolved.packageId.name, `${resolved.packageId.name}/${resolved.packageId.subModuleName}`);
  }
  const file = resolved.resolvedFileName.replace(/\\/g, '/');
  // Keep the resolved filename so aliases cannot hide excluded test sources.
  identities.push(file);
  const nodeModules = file.lastIndexOf('/node_modules/');
  if (nodeModules !== -1) {
    identities.push(file.slice(nodeModules + '/node_modules/'.length));
  }
  const localPath = relative(zeppelinWebAngularRoot, file).replace(/\\/g, '/');
  const project = /^(?:projects|dist)\/zeppelin-([^/]+)(\/.*)?$/.exec(localPath);
  if (project) {
    identities.push(`@zeppelin/${project[1]}${project[2] ?? ''}`);
  }
  if (/^src\/(?:app|environments)\//.test(localPath)) {
    identities.push(`@zeppelin/${localPath}`);
  }
  return identities;
};

const matchesModulePrefix = (moduleSpecifier: string, prefix: string): boolean => {
  return moduleSpecifier === prefix || moduleSpecifier.startsWith(prefix.endsWith('/') ? prefix : `${prefix}/`);
};

const isReferenceIdentifier = (node: ts.Identifier): boolean => {
  const parent = node.parent;
  return !(
    (ts.isPropertyAccessExpression(parent) && parent.name === node) ||
    (ts.isBindingElement(parent) && parent.propertyName === node) ||
    (ts.isPropertyAssignment(parent) && parent.name === node) ||
    (ts.isMethodDeclaration(parent) && parent.name === node) ||
    (ts.isPropertyDeclaration(parent) && parent.name === node) ||
    (ts.isPropertySignature(parent) && parent.name === node) ||
    (ts.isMethodSignature(parent) && parent.name === node)
  );
};

// Keep direct, non-transport browser properties usable, but do not capture the
// entire global object in a local alias that hides later transport access.
// This is a declaration/assignment rule, not general alias or dataflow analysis.
const unwrapExpression = (input: ts.Expression): ts.Expression => {
  let expression = input;
  while (
    ts.isParenthesizedExpression(expression) ||
    ts.isAsExpression(expression) ||
    ts.isTypeAssertionExpression(expression) ||
    ts.isNonNullExpression(expression) ||
    ts.isSatisfiesExpression(expression)
  ) {
    expression = expression.expression;
  }
  return expression;
};

const getGlobalObjectAlias = (node: ts.Node, isGlobal: (node: ts.Identifier) => boolean): string | null => {
  const initializer =
    ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)
      ? node.initializer
      : ts.isBinaryExpression(node) &&
          node.operatorToken.kind === ts.SyntaxKind.EqualsToken &&
          ts.isIdentifier(node.left)
        ? node.right
        : undefined;
  const value = initializer && unwrapExpression(initializer);
  return value && ts.isIdentifier(value) && transportGlobalOwners.has(value.text) && isGlobal(value)
    ? value.text
    : null;
};

const getForbiddenTransportGlobalName = (node: ts.Node, isGlobal: (node: ts.Identifier) => boolean): string | null => {
  const destructuring =
    ts.isVariableDeclaration(node) && ts.isObjectBindingPattern(node.name) && node.initializer
      ? { elements: node.name.elements, value: node.initializer }
      : ts.isBinaryExpression(node) &&
          node.operatorToken.kind === ts.SyntaxKind.EqualsToken &&
          ts.isObjectLiteralExpression(node.left)
        ? { elements: node.left.properties, value: node.right }
        : undefined;
  if (destructuring) {
    const owner = unwrapExpression(destructuring.value);
    if (ts.isIdentifier(owner) && transportGlobalOwners.has(owner.text) && isGlobal(owner)) {
      for (const element of destructuring.elements) {
        const name = ts.isBindingElement(element)
          ? (element.propertyName ?? element.name)
          : ts.isShorthandPropertyAssignment(element) || ts.isPropertyAssignment(element)
            ? element.name
            : undefined;
        if (name && (ts.isIdentifier(name) || ts.isStringLiteral(name)) && forbiddenGlobals.has(name.text)) {
          return name.text;
        }
      }
    }
  }
  if (ts.isIndexedAccessTypeNode(node) && ts.isLiteralTypeNode(node.indexType)) {
    const index = node.indexType.literal;
    let owner = node.objectType;
    while (ts.isParenthesizedTypeNode(owner)) {
      owner = owner.type;
    }
    if (
      (ts.isStringLiteral(index) || ts.isNoSubstitutionTemplateLiteral(index)) &&
      forbiddenGlobals.has(index.text) &&
      ts.isTypeQueryNode(owner) &&
      ts.isIdentifier(owner.exprName) &&
      transportGlobalOwners.has(owner.exprName.text) &&
      isGlobal(owner.exprName)
    ) {
      return index.text;
    }
  }
  if (
    ts.isElementAccessExpression(node) &&
    (ts.isStringLiteral(node.argumentExpression) || ts.isNoSubstitutionTemplateLiteral(node.argumentExpression)) &&
    forbiddenGlobals.has(node.argumentExpression.text)
  ) {
    const owner = unwrapExpression(node.expression);
    if (ts.isIdentifier(owner) && transportGlobalOwners.has(owner.text) && isGlobal(owner)) {
      return node.argumentExpression.text;
    }
  }
  if (ts.isIdentifier(node) && forbiddenGlobals.has(node.text) && isReferenceIdentifier(node) && isGlobal(node)) {
    return node.text;
  }
  if (ts.isPropertyAccessExpression(node) && ts.isIdentifier(node.name) && forbiddenGlobals.has(node.name.text)) {
    const owner = unwrapExpression(node.expression);
    if (ts.isIdentifier(owner) && transportGlobalOwners.has(owner.text) && isGlobal(owner)) {
      return node.name.text;
    }
  }
  return null;
};
