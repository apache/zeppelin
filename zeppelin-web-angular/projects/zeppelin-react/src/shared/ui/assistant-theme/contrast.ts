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

// WCAG 2 contrast for the assistant tokens. antd's palette does not promise it: the accent in dark and the
// tertiary text in light fall short of AA on the panel background.

type Rgb = [number, number, number];
type Rgba = [number, number, number, number];

const parse = (color: string): Rgba => {
  const hex = /^#([\da-f]{3}|[\da-f]{6})$/i.exec(color)?.[1];
  if (hex) {
    const full =
      hex.length === 3
        ? hex
            .split('')
            .map(digit => digit + digit)
            .join('')
        : hex;
    return [parseInt(full.slice(0, 2), 16), parseInt(full.slice(2, 4), 16), parseInt(full.slice(4, 6), 16), 1];
  }
  const parts = /^rgba?\(([^)]+)\)$/i
    .exec(color.trim())?.[1]
    .split(/[\s,/]+/)
    .filter(Boolean)
    .map(Number);
  if (!parts || parts.length < 3 || parts.some(Number.isNaN)) throw new Error(`Unsupported color: ${color}`);
  return [parts[0], parts[1], parts[2], parts[3] ?? 1];
};

const toHex = (rgb: Rgb) => `#${rgb.map(value => Math.round(value).toString(16).padStart(2, '0')).join('')}`;

/** `color` painted over the opaque `background`, as one opaque color. */
export const composite = (color: string, background: string): string => {
  const [r, g, b, a] = parse(color);
  const base = parse(background);
  return toHex([r * a + base[0] * (1 - a), g * a + base[1] * (1 - a), b * a + base[2] * (1 - a)]);
};

const luminance = (color: string) => {
  const channel = (value: number) => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const [r, g, b] = parse(color);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
};

/** Contrast of two opaque colors, 1 to 21. */
export const contrastRatio = (a: string, b: string): number => {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
};

/**
 * `color` (opaque, or translucent over the first background) moved toward `toward` just enough to reach `min`
 * against every background; unchanged when it already does.
 */
export const readable = (color: string, backgrounds: string[], min: number, toward: string): string => {
  const start = composite(color, backgrounds[0]);
  const target = parse(toward);
  const [r, g, b] = parse(start);
  for (let step = 0; step <= 20; step++) {
    const t = step / 20;
    const mixed = toHex([r + (target[0] - r) * t, g + (target[1] - g) * t, b + (target[2] - b) * t]);
    if (backgrounds.every(background => contrastRatio(mixed, composite(background, backgrounds[0])) >= min)) {
      return mixed;
    }
  }
  return toHex([target[0], target[1], target[2]]);
};
