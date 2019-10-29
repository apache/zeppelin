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

import { BaseUrlService } from './base-url.service';

/**
 * @private
 */
export class BaseRest {
  constructor(public baseUrlService: BaseUrlService) {}

  /**
   * ```ts
   * this.restUrl`/user/${username}`
   * this.restUrl(['/user/'], username)
   * this.restUrl(`/user/${username}`)
   * ```
   * @param str`
   * @param values
   */
  restUrl(str: TemplateStringsArray | string, ...values): string {
    let output = this.baseUrlService.getRestApiBase();

    if (typeof str === 'string') {
      return `${output}${str}`;
    }

    let index;
    for (index = 0; index < values.length; index++) {
      output += str[index] + values[index];
    }

    output += str[index];
    return output;
  }
}
