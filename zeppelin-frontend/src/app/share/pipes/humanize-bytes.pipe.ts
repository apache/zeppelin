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

import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'humanizeBytes'
})
export class HumanizeBytesPipe implements PipeTransform {
  transform(value: number): string {
    if (value === null || value === undefined) {
      return '-';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB'];
    const converter = (v: number, p: number): string => {
      const base = Math.pow(1024, p);
      if (v < base) {
        return `${(v / base).toFixed(2)} ${units[p]}`;
      } else if (v < base * 1000) {
        return `${(v / base).toPrecision(3)} ${units[p]}`;
      } else {
        return converter(v, p + 1);
      }
    };
    if (value < 1000) {
      return value + ' B';
    } else {
      return converter(value, 1);
    }
  }
}
