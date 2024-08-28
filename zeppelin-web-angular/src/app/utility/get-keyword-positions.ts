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

import { computeLineStartsMap, getLineAndCharacterFromPosition } from '@zeppelin/utility/line-map';

export interface KeywordPosition {
  line: number;
  character: number;
  length: number;
}

export function getKeywordPositions(keywords: string[], str: string): KeywordPosition[] {
  const highlightPositions = [];
  const lineMap = computeLineStartsMap(str);

  keywords.forEach((keyword: string) => {
    const positions = [];
    const keywordReg = new RegExp(keyword, 'ig');
    let posMatch = keywordReg.exec(str);

    while (posMatch !== null) {
      const { line, character } = getLineAndCharacterFromPosition(lineMap, posMatch.index);
      positions.push({
        line,
        character,
        length: keyword.length
      });
      posMatch = keywordReg.exec(str);
    }
    highlightPositions.push(...positions);
  });

  return highlightPositions;
}
