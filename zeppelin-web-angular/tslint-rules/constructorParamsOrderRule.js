"use strict";
var __extends = (this && this.__extends) || (function () {
    var extendStatics = function (d, b) {
        extendStatics = Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
            function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
        return extendStatics(d, b);
    };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __spreadArrays = (this && this.__spreadArrays) || function () {
    for (var s = 0, i = 0, il = arguments.length; i < il; i++) s += arguments[i].length;
    for (var r = Array(s), k = 0, i = 0; i < il; i++)
        for (var a = arguments[i], j = 0, jl = a.length; j < jl; j++, k++)
            r[k] = a[j];
    return r;
};
exports.__esModule = true;
var ts = require("typescript");
var Lint = require("tslint");
var Rule = /** @class */ (function (_super) {
    __extends(Rule, _super);
    function Rule() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    Rule.prototype.apply = function (sourceFile) {
        return this.applyWithFunction(sourceFile, walk);
    };
    Rule.FAILURE_STRING = "Constructor parameters should be ordered: public, protected, private";
    return Rule;
}(Lint.Rules.AbstractRule));
exports.Rule = Rule;
function walk(ctx) {
    var checkNode = function (node) {
        if (ts.isConstructorDeclaration(node)) {
            var params_1 = node.parameters;
            if (params_1.length <= 1)
                return;
            var rankMap_1 = { public: 0, protected: 1, private: 2, none: 3 };
            var getModifierRank_1 = function (param) { return rankMap_1[getModifier(param)]; };
            var lastRank = -1;
            var needFix = false;
            for (var i = 0; i < params_1.length; i++) {
                var currentRank = getModifierRank_1(params_1[i]);
                if (currentRank < lastRank) {
                    needFix = true;
                    break;
                }
                lastRank = currentRank;
            }
            if (needFix) {
                var sourceText_1 = node.getSourceFile().text;
                // 파라미터 조각 추출
                var paramSlices_1 = params_1.map(function (p, idx) {
                    // 현재 파라미터 시작
                    var start = p.getStart();
                    // 다음 파라미터가 있으면 그 직전까지 포함
                    // (즉, 현재 파라미터 끝 ~ 다음 파라미터 시작 사이의 ,와 공백/주석까지 포함)
                    var end = idx < params_1.length - 1 ? params_1[idx + 1].getStart() : p.getEnd();
                    var text = sourceText_1.slice(start, end);
                    return { node: p, text: text };
                });
                // 정렬
                var sorted = __spreadArrays(paramSlices_1).sort(function (a, b) { return getModifierRank_1(a.node) - getModifierRank_1(b.node); });
                var startLine = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.pos).line;
                var endLine = ctx.sourceFile.getLineAndCharacterOfPosition(node.parameters.end).line;
                var isMultiLine_1 = startLine !== endLine;
                var indent_1 = sorted[0].text.replace(sorted[0].text.trim(), '');
                // 조합
                var fixText = sorted.map(function (s, index) {
                    console.log(s.text);
                    if (index === paramSlices_1.length - 1) {
                        return s.text.replace(',', '');
                    }
                    if (s.text.includes(',')) {
                        return s.text;
                    }
                    if (!s.text.includes('\n') && isMultiLine_1) {
                        console.log('IN??');
                        return s.text + ',' + indent_1;
                    }
                    return s.text + ', ';
                }).join("").trim();
                var fix = Lint.Replacement.replaceFromTo(params_1[0].getStart(), params_1[params_1.length - 1].getEnd(), fixText);
                ctx.addFailureAtNode(node, Rule.FAILURE_STRING, fix);
            }
        }
        ts.forEachChild(node, checkNode);
    };
    ts.forEachChild(ctx.sourceFile, checkNode);
}
function getModifier(param) {
    if (param.modifiers) {
        if (param.modifiers.some(function (m) { return m.kind === ts.SyntaxKind.PublicKeyword; }))
            return "public";
        if (param.modifiers.some(function (m) { return m.kind === ts.SyntaxKind.ProtectedKeyword; }))
            return "protected";
        if (param.modifiers.some(function (m) { return m.kind === ts.SyntaxKind.PrivateKeyword; }))
            return "private";
    }
    return "none";
}
