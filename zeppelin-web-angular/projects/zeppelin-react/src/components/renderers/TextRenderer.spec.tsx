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

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TextRenderer } from './TextRenderer';

describe('TextRenderer', () => {
  it('renders plain text unchanged', () => {
    const { container } = render(<TextRenderer text={'line one\nline two'} />);

    expect(container.querySelector('code')!.textContent).toBe('line one\nline two');
  });

  it('strips ANSI escapes and colors the text they wrap', () => {
    const { container } = render(<TextRenderer text={'\x1b[31mred\x1b[0m plain'} />);

    expect(container.querySelector('code')!.textContent).toBe('red plain');
    expect(screen.getByText('red').style.color).toBe('rgb(187, 0, 0)');
    expect(screen.getByText('plain').style.color).toBe('');
  });

  it('applies background color and every active decoration', () => {
    render(<TextRenderer text={'\x1b[1;4;9;42mstyled\x1b[0m'} />);

    const span = screen.getByText('styled');
    expect(span.style.backgroundColor).toBe('rgb(0, 187, 0)');
    expect(span.style.fontWeight).toBe('bold');
    expect(span.style.textDecoration).toBe('underline line-through');
  });

  it('leaves markup in the text literal', () => {
    const { container } = render(<TextRenderer text={'<b>not bold</b>'} />);

    expect(container.querySelector('b')).toBeNull();
    expect(screen.getByText('<b>not bold</b>')).toBeTruthy();
  });
});
