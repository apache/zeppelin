import { OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

export class DestroyHookComponent implements OnDestroy {
  readonly destroy$ = new Subject();

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
