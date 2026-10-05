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

import Anser from 'anser';
import { CSSProperties } from 'react';

export interface TextRendererProps {
  text: string;
}

const toStyle = ({ fg, bg, decorations }: Anser.AnserJsonEntry): CSSProperties => {
  const style: CSSProperties = {};
  const textDecorations: string[] = [];
  if (fg) {
    style.color = `rgb(${fg})`;
  }
  if (bg) {
    style.backgroundColor = `rgb(${bg})`;
  }
  decorations.forEach(decoration => {
    switch (decoration) {
      case 'bold':
        style.fontWeight = 'bold';
        break;
      case 'dim':
        style.opacity = 0.5;
        break;
      case 'italic':
        style.fontStyle = 'italic';
        break;
      case 'hidden':
        style.visibility = 'hidden';
        break;
      case 'underline':
        textDecorations.push('underline');
        break;
      case 'strikethrough':
        textDecorations.push('line-through');
        break;
      case 'blink':
        textDecorations.push('blink');
        break;
    }
  });
  if (textDecorations.length > 0) {
    style.textDecoration = textDecorations.join(' ');
  }
  return style;
};

// Matches Angular: result.component.ts renderText()
// Carriage returns are resolved by the caller (checkAndReplaceCarriageReturn).
export const TextRenderer = ({ text }: TextRendererProps) => {
  const entries = Anser.ansiToJson(text, { json: true, remove_empty: true });
  return (
    <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
      <code>
        {entries.map((entry, i) => (
          <span key={i} style={toStyle(entry)}>
            {entry.content}
          </span>
        ))}
      </code>
    </pre>
  );
};
