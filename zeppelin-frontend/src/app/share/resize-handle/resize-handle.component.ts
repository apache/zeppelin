import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'zeppelin-resize-handle',
  templateUrl: './resize-handle.component.html',
  styleUrls: ['./resize-handle.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'resize-handle'
  }
})
export class ResizeHandleComponent {
  constructor() {}
}
