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

import { isNil } from 'lodash';
import { KeyCode, KeyMod } from 'monaco-editor';

const ASCII_MAX = 128;

function isAscii(ch: string): boolean {
  if (ch.length !== 1) {
    throw new Error('Only single character is allowed');
  }
  return ch.charCodeAt(0) < ASCII_MAX;
}

export class KeyCodeConverter {
  private static angularToMonacoKeyConverter: Record<string, number> = {
    control: KeyMod.WinCtrl,
    alt: KeyMod.Alt,
    shift: KeyMod.Shift,
    a: KeyCode.KeyA,
    b: KeyCode.KeyB,
    c: KeyCode.KeyC,
    d: KeyCode.KeyD,
    e: KeyCode.KeyE,
    f: KeyCode.KeyF,
    g: KeyCode.KeyG,
    h: KeyCode.KeyH,
    i: KeyCode.KeyI,
    j: KeyCode.KeyJ,
    k: KeyCode.KeyK,
    l: KeyCode.KeyL,
    m: KeyCode.KeyM,
    n: KeyCode.KeyN,
    o: KeyCode.KeyO,
    p: KeyCode.KeyP,
    q: KeyCode.KeyQ,
    r: KeyCode.KeyR,
    s: KeyCode.KeyS,
    t: KeyCode.KeyT,
    u: KeyCode.KeyU,
    v: KeyCode.KeyV,
    w: KeyCode.KeyW,
    x: KeyCode.KeyX,
    y: KeyCode.KeyY,
    z: KeyCode.KeyZ,
    0: KeyCode.Digit0,
    1: KeyCode.Digit1,
    2: KeyCode.Digit2,
    3: KeyCode.Digit3,
    4: KeyCode.Digit4,
    5: KeyCode.Digit5,
    6: KeyCode.Digit6,
    7: KeyCode.Digit7,
    8: KeyCode.Digit8,
    9: KeyCode.Digit9,
    ';': KeyCode.Semicolon,
    '=': KeyCode.Equal,
    ',': KeyCode.Comma,
    '-': KeyCode.Minus,
    '.': KeyCode.Period,
    '/': KeyCode.Slash,
    '`': KeyCode.Backquote,
    '[': KeyCode.BracketLeft,
    ']': KeyCode.BracketRight,
    '\\': KeyCode.Backslash,
    "'": KeyCode.Quote,
    enter: KeyCode.Enter,
    escape: KeyCode.Escape,
    backspace: KeyCode.Backspace,
    tab: KeyCode.Tab,
    space: KeyCode.Space,
    arrowup: KeyCode.UpArrow,
    arrowdown: KeyCode.DownArrow,
    arrowleft: KeyCode.LeftArrow,
    arrowright: KeyCode.RightArrow,
    delete: KeyCode.Delete,
    home: KeyCode.Home,
    end: KeyCode.End,
    pageup: KeyCode.PageUp,
    pagedown: KeyCode.PageDown,
    insert: KeyCode.Insert
  };

  // Characters that are typed with `Shift` key could be handled with non-shifted version in keybinding.
  private static exclusions = ['_', '+', '"', '{', '}', '|'];

  static angularToMonacoKeyBinding(keybinding: string) {
    const parts = keybinding.split('.');
    // Ignore non-ASCII characters. Non-ASCII characters are just for macOS compatibility.
    // Monaco editor handles pressing `Option(Alt) + a letter` not to convert a letter to non-ASCII character.
    if (parts.some(p => p.length === 1 && (!isAscii(p) || this.exclusions.includes(p)))) {
      return null;
    }

    // All ASCII characters should be supported.
    // If this error is thrown, it means that `angularToMonacoKeyConverter` should be updated.
    if (
      parts.some(
        p => p.length === 1 && !this.exclusions.includes(p) && isAscii(p) && !(p in this.angularToMonacoKeyConverter)
      )
    ) {
      throw new Error(`Unsupported keybinding: '${keybinding}'.`);
    }

    const convertedParts = parts.map(p => {
      const converted = this.angularToMonacoKeyConverter[p];
      if (isNil(converted)) {
        throw new Error(`Key code should be defined for '${p}'.`);
      }
      return converted;
    });

    return convertedParts.reduce((acc, part) => acc | part, 0);
  }
}
