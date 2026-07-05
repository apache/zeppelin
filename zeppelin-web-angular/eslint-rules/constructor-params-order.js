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

// ESLint reimplementation of the former custom TSLint `constructor-params-order`
// rule (ZEPPELIN-6301). Enforces the constructor parameter order
// public -> protected -> private (-> none -> optional) for a consistent New UI
// constructor style. ESLint replaces TSLint under ZEPPELIN-6372.

'use strict';

const RANK = { public: 0, protected: 1, private: 2, none: 3, optional: 4 };

/**
 * Collect decorators attached to a (possibly parameter-property) parameter node.
 * @param {any} param
 * @returns {any[]}
 */
function getDecorators(param) {
  if (Array.isArray(param.decorators) && param.decorators.length > 0) {
    return param.decorators;
  }
  // TSParameterProperty wraps the real parameter in `.parameter`.
  if (param.parameter && Array.isArray(param.parameter.decorators)) {
    return param.parameter.decorators;
  }
  return [];
}

/**
 * True when the parameter is optional -- either annotated with `@Optional()`
 * (Angular DI) or declared with a `?` question token.
 * @param {any} param
 * @returns {boolean}
 */
function isOptional(param) {
  const hasOptionalDecorator = getDecorators(param).some(decorator => {
    let expr = decorator.expression;
    if (expr && expr.type === 'CallExpression') {
      expr = expr.callee;
    }
    return expr && expr.type === 'Identifier' && expr.name === 'Optional';
  });
  if (hasOptionalDecorator) {
    return true;
  }
  const inner = param.type === 'TSParameterProperty' ? param.parameter : param;
  return !!(inner && inner.optional);
}

/**
 * Map a constructor parameter to its accessibility rank.
 * @param {any} param
 * @returns {number}
 */
function getRank(param) {
  if (isOptional(param)) {
    return RANK.optional;
  }
  if (param.type === 'TSParameterProperty') {
    // A parameter property with no explicit accessibility (e.g. `readonly x`)
    // is implicitly public.
    return RANK[param.accessibility || 'public'];
  }
  return RANK.none;
}

/**
 * Source range of a parameter's "content": the parameter itself plus its
 * leading decorators/comments and any trailing comment that sits before the
 * following comma. Reordering moves these content ranges between fixed slots
 * (keeping the original commas and whitespace in place), so comments travel
 * with their parameter -- the same guarantee the original TSLint fixer gave by
 * slicing from getFullStart() to getFullStart().
 * @param {any} param
 * @param {import('eslint').SourceCode} sourceCode
 * @returns {[number, number]}
 */
function getContentRange(param, sourceCode) {
  let start = param.range[0];
  let headNode = param;
  for (const decorator of getDecorators(param)) {
    if (decorator.range[0] < start) {
      start = decorator.range[0];
      headNode = decorator;
    }
  }
  const leading = sourceCode.getCommentsBefore(headNode);
  if (leading.length > 0 && leading[0].range[0] < start) {
    start = leading[0].range[0];
  }
  let end = param.range[1];
  const nextToken = sourceCode.getTokenAfter(param);
  for (const comment of sourceCode.getCommentsAfter(param)) {
    if (!nextToken || comment.range[1] <= nextToken.range[0]) {
      end = Math.max(end, comment.range[1]);
    }
  }
  return [start, end];
}

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Enforce constructor parameter order: public, protected, private'
    },
    fixable: 'code',
    schema: [],
    messages: {
      order: 'Constructor parameters should be ordered: public, protected, private'
    }
  },

  create(context) {
    const sourceCode = context.sourceCode || context.getSourceCode();

    /**
     * @param {any} node MethodDefinition with kind === 'constructor'
     */
    function checkConstructor(node) {
      const params = node.value.params;
      if (!params || params.length <= 1) {
        return;
      }

      const ranks = params.map(getRank);
      const inOrder = ranks.every((rank, i) => i === 0 || ranks[i - 1] <= rank);
      if (inOrder) {
        return;
      }

      context.report({
        node,
        messageId: 'order',
        fix(fixer) {
          const text = sourceCode.getText();
          const ranges = params.map(p => getContentRange(p, sourceCode));
          // Stable sort by rank preserves the original relative order within a
          // group, matching the previous TSLint behaviour.
          const order = params.map((_, i) => i).sort((a, b) => ranks[a] - ranks[b]);

          // Drop the sorted parameter content into the fixed slots, keeping the
          // original separators (commas, whitespace, indentation) between slots
          // untouched. Only the parameter text moves, so formatting and inter-
          // parameter comments are preserved.
          let out = '';
          for (let slot = 0; slot < params.length; slot++) {
            const src = ranges[order[slot]];
            out += text.slice(src[0], src[1]);
            if (slot < params.length - 1) {
              out += text.slice(ranges[slot][1], ranges[slot + 1][0]);
            }
          }
          return fixer.replaceTextRange([ranges[0][0], ranges[params.length - 1][1]], out);
        }
      });
    }

    return {
      'MethodDefinition[kind="constructor"]': checkConstructor
    };
  }
};
