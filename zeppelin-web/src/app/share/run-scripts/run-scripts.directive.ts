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

import { Directive, ElementRef, Input, NgZone, OnChanges, Renderer2, SimpleChanges } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { take } from 'rxjs/operators';

const loadedExternalScripts = new Set<string>();

@Directive({
  selector: '[zeppelinRunScripts]'
})
export class RunScriptsDirective implements OnChanges {
  @Input() scriptsContent: string | SafeHtml;

  constructor(private elementRef: ElementRef<HTMLElement>, private ngZone: NgZone, private renderer: Renderer2) {}

  runScripts(): void {
    if (!this.scriptsContent.toString()) {
      return;
    }
    this.ngZone.onStable.pipe(take(1)).subscribe(() => {
      this.ngZone.runOutsideAngular(() => {
        const scripts = this.elementRef.nativeElement.getElementsByTagName('script');
        const externalScripts = [];
        const localScripts = [];
        for (let i = 0; i < scripts.length; i++) {
          const script = scripts[i];
          if (script.text) {
            localScripts.push(script);
          } else if (script.src) {
            externalScripts.push(script);
          }
          this.renderer.removeChild(this.elementRef.nativeElement, script);
        }
        Promise.all(externalScripts.map(s => this.loadExternalScript(s, this.elementRef.nativeElement))).then(() => {
          localScripts.forEach(s => this.loadLocalScript(s, this.elementRef.nativeElement));
        });
      });
    });
  }

  loadExternalScript(script: HTMLScriptElement, parentNode: HTMLElement): Promise<void> {
    return new Promise<void>(resolve => {
      if (loadedExternalScripts.has(script.src)) {
        resolve();
      }
      const scriptCopy = this.renderer.createElement('script') as HTMLScriptElement;
      scriptCopy.type = script.type ? script.type : 'text/javascript';
      scriptCopy.src = script.src;
      scriptCopy.onload = () => {
        resolve();
        loadedExternalScripts.add(script.src);
      };
      parentNode.appendChild(scriptCopy);
    });
  }

  loadLocalScript(script: HTMLScriptElement, parentNode: HTMLElement): void {
    const scriptCopy = this.renderer.createElement('script') as HTMLScriptElement;
    scriptCopy.type = script.type ? script.type : 'text/javascript';
    scriptCopy.text = `(function() { ${script.text} })();`;
    parentNode.appendChild(scriptCopy);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.scriptsContent) {
      this.runScripts();
    }
  }
}
