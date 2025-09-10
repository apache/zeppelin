/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as Lint from 'tslint';
import * as ts from 'typescript';

interface OptionsType {
  targetFiles?: string[];
}

export class Rule extends Lint.Rules.AbstractRule {
  public static FAILURE_STRING = 'Export statements should be alphabetically ordered by module specifier';

  public apply(sourceFile: ts.SourceFile): Lint.RuleFailure[] {
    return this.applyWithFunction(sourceFile, walk, this.getOptions() as OptionsType);
  }
}

function walk(ctx: Lint.WalkContext<OptionsType>) {
  const targetFiles = ctx.options.targetFiles || [];
  const fileName = ctx.sourceFile.fileName;

  const shouldApply = targetFiles.some(targetFile => fileName.endsWith(targetFile));

  if (!shouldApply) {
    return;
  }

  const exportStatements: Array<{
    node: ts.Node;
    sortKey: string;
    start: number;
    end: number;
  }> = [];

  const visit = (node: ts.Node) => {
    if (isExportStatement(node)) {
      const sortKey = getSortKey(node);
      if (sortKey) {
        exportStatements.push({
          node,
          sortKey,
          start: node.getStart(),
          end: node.getEnd()
        });
      }
    }
    ts.forEachChild(node, visit);
  };

  visit(ctx.sourceFile);

  if (exportStatements.length <= 1) {
    return;
  }

  const isSorted = exportStatements.every(
    (statement, i, arr) =>
      i === 0 ||
      statement.sortKey.localeCompare(arr[i - 1].sortKey, undefined, {
        sensitivity: 'base'
      }) >= 0
  );

  if (!isSorted) {
    const sortedExports = [...exportStatements].sort((a, b) =>
      a.sortKey.localeCompare(b.sortKey, undefined, { sensitivity: 'base' })
    );

    const sourceText = ctx.sourceFile.text;
    const fixText = sortedExports
      .map(exportStatement => sourceText.substring(exportStatement.start, exportStatement.end).trim())
      .join('\n');

    const firstExport = exportStatements[0];
    const lastExport = exportStatements[exportStatements.length - 1];

    const fix = Lint.Replacement.replaceFromTo(firstExport.start, lastExport.end, fixText);

    ctx.addFailureAtNode(firstExport.node, Rule.FAILURE_STRING, fix);
  }
}

function hasExportModifier(node: ts.Node): boolean {
  return !!node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword);
}

function isExportStatement(node: ts.Node): boolean {
  return ts.isExportDeclaration(node) || ts.isExportAssignment(node) || hasExportModifier(node);
}

function getSortKey(node: ts.Node): string | null {
  if (ts.isExportDeclaration(node)) {
    if (node.moduleSpecifier) {
      return node.moduleSpecifier.getText().replace(/['"]/g, '');
    } else if (node.exportClause && ts.isNamedExports(node.exportClause)) {
      return node.exportClause.elements.map(el => el.name.getText()).join(', ');
    }
  }

  if (ts.isExportAssignment(node)) {
    return 'export-assignment';
  }

  if (ts.isVariableStatement(node)) {
    return node.declarationList.declarations[0]?.name.getText() ?? null;
  }

  if (
    (ts.isFunctionDeclaration(node) ||
      ts.isClassDeclaration(node) ||
      ts.isInterfaceDeclaration(node) ||
      ts.isTypeAliasDeclaration(node) ||
      ts.isEnumDeclaration(node)) &&
    node.name
  ) {
    return node.name.getText();
  }

  return null;
}
