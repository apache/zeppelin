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
