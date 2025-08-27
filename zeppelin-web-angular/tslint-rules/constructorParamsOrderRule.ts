import * as ts from "typescript";
import * as Lint from "tslint";

export class Rule extends Lint.Rules.AbstractRule {
  public static FAILURE_STRING = "Constructor parameters should be ordered: public, protected, private";

  public apply(sourceFile: ts.SourceFile): Lint.RuleFailure[] {
    return this.applyWithFunction(sourceFile, walk);
  }
}

function walk(ctx: Lint.WalkContext<void>) {
  const checkNode = (node: ts.Node) => {
    if (ts.isConstructorDeclaration(node)) {
      const params = node.parameters;
      if (params.length <= 1) return;

      const rankMap: Record<string, number> = { public: 0, protected: 1, private: 2, none: 3 };
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
          const start = p.getStart();

          const end = idx < params.length - 1 ? params[idx + 1].getStart() : p.getEnd();

          const text = sourceText.slice(start, end);
          return { node: p, text };
        });

        // For sort
        const sorted = [...paramSlices].sort(
          (a, b) => getModifierRank(a.node) - getModifierRank(b.node)
        );

        const { line: startLine } = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.pos);
        const { line: endLine } = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.end);
        const isMultiLine = startLine !== endLine;
        let indent = sorted[0].text.replace(sorted[0].text.trim(), '');

        // For recombination
        const fixText = sorted.map((s, index) => {
          if (index === paramSlices.length - 1) {
            return s.text.replace(',', '')
          }

          if (s.text.includes(',')) {
            return s.text;
          }

          if (!s.text.includes('\n') && isMultiLine) {
            return s.text + ',' + indent;
          }

          return s.text + ', ';
        }).join("").trim();

        const fix = Lint.Replacement.replaceFromTo(
          params[0].getStart(),
          params[params.length - 1].getEnd(),
          fixText
        );

        ctx.addFailureAtNode(node, Rule.FAILURE_STRING, fix);
      }
    }
    ts.forEachChild(node, checkNode);
  };
  ts.forEachChild(ctx.sourceFile, checkNode);
}

function getModifier(param: ts.ParameterDeclaration): string {
  if (param.modifiers) {
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.PublicKeyword)) return "public";
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.ProtectedKeyword)) return "protected";
    if (param.modifiers.some(m => m.kind === ts.SyntaxKind.PrivateKeyword)) return "private";
  }
  return "none";
}