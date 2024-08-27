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

const LF_CHAR = 10;
const CR_CHAR = 13;
const LINE_SEP_CHAR = 8232;
const PARAGRAPH_CHAR = 8233;

export function computeLineStartsMap(text) {
  const result = [0];
  let pos = 0;
  while (pos < text.length) {
    const char = text.charCodeAt(pos++);
    // Handles the "CRLF" line break. In that case we peek the character
    // after the "CR" and check if it is a line feed.
    if (char === CR_CHAR) {
      if (text.charCodeAt(pos) === LF_CHAR) {
        pos++;
      }
      result.push(pos);
    } else if (char === LF_CHAR || char === LINE_SEP_CHAR || char === PARAGRAPH_CHAR) {
      result.push(pos);
    }
  }
  result.push(pos);
  return result;
}

function findClosestLineStartPosition(linesMap, position, low = 0, high = linesMap.length - 1) {
  let _low = low;
  let _high = high;
  while (_low <= _high) {
    const pivotIdx = Math.floor((_low + _high) / 2);
    const pivotEl = linesMap[pivotIdx];
    if (pivotEl === position) {
      return pivotIdx;
    } else if (position > pivotEl) {
      _low = pivotIdx + 1;
    } else {
      _high = pivotIdx - 1;
    }
  }
  // In case there was no exact match, return the closest "lower" line index. We also
  // subtract the index by one because want the index of the previous line start.
  return _low - 1;
}

export function getLineAndCharacterFromPosition(lineStartsMap, position) {
  const lineIndex = findClosestLineStartPosition(lineStartsMap, position);
  return { character: position - lineStartsMap[lineIndex], line: lineIndex };
}
