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

import { Directive, HostBinding, Input, ElementRef } from '@angular/core';

@Directive({
  selector: 'a[href]',
})
export class ExternalLinkDirective {
  @HostBinding('attr.rel') relAttr = null;
  @HostBinding('attr.target') targetAttr = null;
  @Input() href: string;

  constructor(private elementRef: ElementRef) {}

  ngOnChanges() {
    this.elementRef.nativeElement.href = this.href;

    if (this.isLinkExternal()) {
      // https://developers.google.com/web/tools/lighthouse/audits/noopener
      this.relAttr = 'noopener noreferrer';
      this.targetAttr = '_blank';
    } else {
      this.relAttr = null;
      this.targetAttr = null;
    }
  }

  private isLinkExternal() {
    return !this.elementRef.nativeElement.hostname.includes(location.hostname);
  }
}
