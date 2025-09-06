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

import * as Lint from 'tslint';
import * as ts from 'typescript';

export class Rule extends Lint.Rules.AbstractRule {
  public static FAILURE_STRING = 'Constructor parameters should be ordered: public, protected, private';

  public apply(sourceFile: ts.SourceFile): Lint.RuleFailure[] {
    return this.applyWithFunction(sourceFile, walk);
  }
}

function walk(ctx: Lint.WalkContext<void>) {
  const checkNode = (node: ts.Node) => {
    if (ts.isConstructorDeclaration(node)) {
      const params = node.parameters;
      if (params.length <= 1) {
        return;
      }

      const rankMap: Record<string, number> = { public: 0, protected: 1, private: 2, none: 3, optional: 4 };
      const getModifierRank = (param: ts.ParameterDeclaration) => rankMap[getModifier(param)];

      let lastRank = -1;
      let needFix = false;
      for (let i = 0; i < params.length; i++) {
        const currentRank = getModifierRank(params[i]);
        if (currentRank < lastRank) {
          needFix = true;
          break;
        }
        lastRank = currentRank;
      }

      if (needFix) {
        const sourceText = node.getSourceFile().text;

        // For keeping comment
        const paramSlices = params.map((p, idx) => {
          const start = p.getFullStart();

          const end = idx < params.length - 1 ? params[idx + 1].getFullStart() : p.getEnd();

          const text = sourceText.slice(start, end);
          return { node: p, text };
        });

        // For sort
        const sorted = [...paramSlices].sort((a, b) => getModifierRank(a.node) - getModifierRank(b.node));

        const { line: startLine } = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.pos);
        const { line: endLine } = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.end);
        const isMultiLine = startLine !== endLine;
        const indent = sorted[0].text.replace(sorted[0].text.trim(), '');

        // For recombination
        const fixText = sorted
          .map((s, index) => {
            if (index === paramSlices.length - 1) {
              return s.text.replace(',', '');
            }

            if (s.text.includes(',')) {
              return s.text;
            }

            if (!s.text.includes('\n') && isMultiLine) {
              return `${s.text},${indent}`;
            }

            return s.text + ', ';
          })
          .join('')
          .trim();

        const fix = Lint.Replacement.replaceFromTo(params[0].getStart(), params[params.length - 1].getEnd(), fixText);

        ctx.addFailureAtNode(node, Rule.FAILURE_STRING, fix);
      }
    }
    ts.forEachChild(node, checkNode);
  };
  ts.forEachChild(ctx.sourceFile, checkNode);
}

function getModifier(param: ts.ParameterDeclaration): string {
  const hasOptional =
    param.decorators?.some(d => {
      let expr = d.expression;
      if (ts.isCallExpression(expr)) {
        expr = expr.expression;
      }
      return ts.isIdentifier(expr) && expr.text === 'Optional';
    }) || !!param.questionToken;

  if (hasOptional) {
    return 'optional';
  }
  if (param.modifiers) {
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.PublicKeyword)) {
      return 'public';
    }
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.ProtectedKeyword)) {
      return 'protected';
    }
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.PrivateKeyword)) {
      return 'private';
    }
  }
  return 'none';
}
