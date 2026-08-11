/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const WEB_ROOT = path.resolve(__dirname, '..');
const REPOSITORY_ROOT = path.resolve(WEB_ROOT, '..');
const JAVA_OPERATOR_SOURCE = path.join(
  REPOSITORY_ROOT,
  'zeppelin-common/src/main/java/org/apache/zeppelin/common/Message.java'
);
const TYPESCRIPT_OPERATOR_SOURCE = path.join(
  WEB_ROOT,
  'projects/zeppelin-sdk/src/interfaces/message-operator.interface.ts'
);
const DATA_TYPE_MAP_SOURCE = path.join(
  WEB_ROOT,
  'projects/zeppelin-sdk/src/interfaces/message-data-type-map.interface.ts'
);
const OPERATION_NAME = /^[A-Z][A-Z0-9_]*$/;
const FRONTEND_ONLY_TAG = 'frontendOnly';

function fail(message) {
  throw new Error(`Websocket contract check failed: ${message}`);
}

function addOperation(operations, name, description) {
  if (!OPERATION_NAME.test(name)) {
    fail(`${description} contains invalid operation '${name}'`);
  }
  if (operations.has(name)) {
    fail(`${description} contains duplicate operation ${name}`);
  }
  operations.add(name);
}

function readJavaEnumBody(source, file) {
  const declarationPattern = /^\s*public\s+enum\s+OP\s*\{/gm;
  const declarations = [...source.matchAll(declarationPattern)];
  if (declarations.length !== 1) {
    fail(`Expected exactly one public enum OP in ${file}, found ${declarations.length}`);
  }

  const declaration = declarations[0];
  const openingBrace = declaration.index + declaration[0].lastIndexOf('{');
  let state = 'code';
  let body = '';

  for (let index = openingBrace + 1; index < source.length; index += 1) {
    const character = source[index];
    const nextCharacter = source[index + 1];

    if (state === 'line-comment') {
      if (character === '\n') {
        state = 'code';
        body += '\n';
      }
      continue;
    }

    if (state === 'block-comment') {
      if (character === '*' && nextCharacter === '/') {
        state = 'code';
        body += ' ';
        index += 1;
      }
      continue;
    }

    if (character === '/' && nextCharacter === '/') {
      state = 'line-comment';
      index += 1;
      continue;
    }
    if (character === '/' && nextCharacter === '*') {
      state = 'block-comment';
      index += 1;
      continue;
    }
    if (character === '}') {
      return body;
    }
    if (character === '{' || character === '"' || character === "'") {
      fail(`${file} enum OP must contain only simple enum constants`);
    }
    body += character;
  }

  const detail = state === 'block-comment' ? 'an unclosed block comment' : 'no closing brace';
  fail(`Could not parse enum OP in ${file}: ${detail}`);
}

function parseJavaOperations(source, file = 'Message.java') {
  let body = readJavaEnumBody(source, file).trim();
  if (body.endsWith(';')) {
    body = body.slice(0, -1).trimEnd();
  }

  const entries = body.split(',');
  if (entries.at(-1).trim() === '') {
    entries.pop();
  }
  if (entries.length === 0) {
    fail(`${file} enum OP must contain at least one operation`);
  }

  const operations = new Set();
  for (const entry of entries) {
    const name = entry.trim();
    if (name === '') {
      fail(`${file} enum OP contains an empty operation`);
    }
    addOperation(operations, name, `${file} enum OP`);
  }
  return operations;
}

function parseTypeScript(source, file) {
  const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  if (sourceFile.parseDiagnostics.length) {
    const diagnostics = sourceFile.parseDiagnostics
      .map(diagnostic => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'))
      .join('; ');
    fail(`Could not parse ${file}: ${diagnostics}`);
  }
  return sourceFile;
}

function findSingleDeclaration(sourceFile, predicate, description) {
  const declarations = sourceFile.statements.filter(predicate);
  if (declarations.length !== 1) {
    fail(`Expected exactly one ${description} in ${sourceFile.fileName}, found ${declarations.length}`);
  }
  return declarations[0];
}

function jsDocCommentText(comment) {
  if (typeof comment === 'string') {
    return comment;
  }
  if (Array.isArray(comment)) {
    return comment.map(part => part.text).join('');
  }
  return '';
}

function parseTypeScriptOperations(source, file = 'message-operator.interface.ts') {
  const sourceFile = parseTypeScript(source, file);
  const declaration = findSingleDeclaration(
    sourceFile,
    statement => ts.isEnumDeclaration(statement) && statement.name.text === 'OP',
    'enum OP'
  );
  const wireOperations = new Set();
  const frontendOnlyOperations = new Set();
  const allOperations = new Set();

  for (const member of declaration.members) {
    if (!ts.isIdentifier(member.name) || !member.initializer || !ts.isStringLiteral(member.initializer)) {
      fail('Every TypeScript OP member must be an identifier with an explicit string literal value');
    }

    const name = member.name.text;
    if (name !== member.initializer.text) {
      fail(`OP.${name} must use the wire value '${name}', found '${member.initializer.text}'`);
    }
    addOperation(allOperations, name, 'TypeScript enum OP');

    const frontendOnlyTags = ts.getJSDocTags(member).filter(tag => tag.tagName.text === FRONTEND_ONLY_TAG);
    if (frontendOnlyTags.length > 1) {
      fail(`OP.${name} has multiple @${FRONTEND_ONLY_TAG} tags`);
    }
    if (frontendOnlyTags.length === 1) {
      if (jsDocCommentText(frontendOnlyTags[0].comment).trim() === '') {
        fail(`OP.${name} must explain why it is @${FRONTEND_ONLY_TAG}`);
      }
      frontendOnlyOperations.add(name);
    } else {
      wireOperations.add(name);
    }
  }

  return { allOperations, frontendOnlyOperations, wireOperations };
}

function parseDataTypeMapOperations(source, interfaceName, file = 'message-data-type-map.interface.ts') {
  const sourceFile = parseTypeScript(source, file);
  const declaration = findSingleDeclaration(
    sourceFile,
    statement => ts.isInterfaceDeclaration(statement) && statement.name.text === interfaceName,
    `interface ${interfaceName}`
  );
  if (declaration.heritageClauses && declaration.heritageClauses.length) {
    fail(`interface ${interfaceName} must not use inheritance`);
  }

  const operations = new Set();
  for (const member of declaration.members) {
    if (
      !ts.isPropertySignature(member) ||
      !member.name ||
      !ts.isComputedPropertyName(member.name) ||
      !ts.isPropertyAccessExpression(member.name.expression) ||
      !ts.isIdentifier(member.name.expression.expression) ||
      member.name.expression.expression.text !== 'OP' ||
      !ts.isIdentifier(member.name.expression.name)
    ) {
      fail(`Every ${interfaceName} member must use the form [OP.NAME]: Type`);
    }
    addOperation(operations, member.name.expression.name.text, interfaceName);
  }
  return operations;
}

function difference(left, right) {
  return [...left].filter(value => !right.has(value)).sort();
}

function validateContract(javaOperations, typeScriptOperations, sendOperations, receiveOperations) {
  const overlap = [...typeScriptOperations.frontendOnlyOperations]
    .filter(operation => javaOperations.has(operation))
    .sort();
  if (overlap.length) {
    fail(`Java wire operations cannot be @${FRONTEND_ONLY_TAG}: [${overlap.join(', ')}]`);
  }

  const missing = difference(javaOperations, typeScriptOperations.wireOperations);
  const extra = difference(typeScriptOperations.wireOperations, javaOperations);
  if (missing.length || extra.length) {
    fail(
      `Java Message.OP and TypeScript wire OP differ; ` +
        `missing from TypeScript=[${missing.join(', ')}], extra in TypeScript=[${extra.join(', ')}]`
    );
  }

  for (const operation of sendOperations) {
    if (typeScriptOperations.frontendOnlyOperations.has(operation)) {
      fail(`Frontend-only operation ${operation} cannot be in MessageSendDataTypeMap`);
    }
    if (!javaOperations.has(operation)) {
      fail(`MessageSendDataTypeMap operation ${operation} is not a Java wire operation`);
    }
  }

  for (const operation of receiveOperations) {
    if (!typeScriptOperations.allOperations.has(operation)) {
      fail(`MessageReceiveDataTypeMap operation ${operation} is not declared in TypeScript enum OP`);
    }
  }

  for (const operation of typeScriptOperations.frontendOnlyOperations) {
    if (!receiveOperations.has(operation)) {
      fail(`Frontend-only operation ${operation} must be in MessageReceiveDataTypeMap`);
    }
  }
}

function main() {
  const javaOperations = parseJavaOperations(fs.readFileSync(JAVA_OPERATOR_SOURCE, 'utf8'), JAVA_OPERATOR_SOURCE);
  const typeScriptOperations = parseTypeScriptOperations(
    fs.readFileSync(TYPESCRIPT_OPERATOR_SOURCE, 'utf8'),
    TYPESCRIPT_OPERATOR_SOURCE
  );
  const dataTypeMapSource = fs.readFileSync(DATA_TYPE_MAP_SOURCE, 'utf8');
  const sendOperations = parseDataTypeMapOperations(dataTypeMapSource, 'MessageSendDataTypeMap', DATA_TYPE_MAP_SOURCE);
  const receiveOperations = parseDataTypeMapOperations(
    dataTypeMapSource,
    'MessageReceiveDataTypeMap',
    DATA_TYPE_MAP_SOURCE
  );

  validateContract(javaOperations, typeScriptOperations, sendOperations, receiveOperations);
  console.log(
    `Websocket contract is synchronized: ${javaOperations.size} wire operations, ` +
      `${typeScriptOperations.frontendOnlyOperations.size} frontend-only operation(s).`
  );
}

module.exports = {
  parseDataTypeMapOperations,
  parseJavaOperations,
  parseTypeScriptOperations,
  validateContract
};

if (require.main === module) {
  main();
}
