import { AfterViewChecked, Directive, ElementRef } from '@angular/core';

@Directive({
  selector: '[zeppelinMathJax]'
})
export class MathJaxDirective implements AfterViewChecked {
  constructor(private el: ElementRef<HTMLElement>) {}

  ngAfterViewChecked(): void {
    MathJax.Hub.Queue(['Typeset', MathJax.Hub, this.el.nativeElement]);
  }
}
