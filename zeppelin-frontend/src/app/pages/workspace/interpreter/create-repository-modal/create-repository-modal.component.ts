import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { takeUntil } from 'rxjs/operators';

import { NzModalRef } from 'ng-zorro-antd';

import { DestroyHookComponent } from '@zeppelin/core';
import { CreateInterpreterRepositoryForm } from '@zeppelin/interfaces';
import { InterpreterService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-interpreter-create-repository-modal',
  templateUrl: './create-repository-modal.component.html',
  styleUrls: ['./create-repository-modal.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InterpreterCreateRepositoryModalComponent extends DestroyHookComponent implements OnInit {
  validateForm: FormGroup;
  submitting = false;
  urlProtocol = 'http://';

  handleCancel() {
    this.nzModalRef.close();
  }

  handleSubmit() {
    const data = this.validateForm.getRawValue() as CreateInterpreterRepositoryForm;
    // set url protocol
    data.url = `${this.urlProtocol}${data.url}`;
    // reset proxy port
    const proxyPort = Number.parseInt(data.proxyPort, 10);
    data.proxyPort = Number.isNaN(proxyPort) ? null : `${proxyPort}`;
    this.interpreterService
      .addRepository(data)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.nzModalRef.close('Done');
      });
  }

  constructor(
    private formBuilder: FormBuilder,
    private nzModalRef: NzModalRef,
    private interpreterService: InterpreterService
  ) {
    super();
  }

  ngOnInit() {
    this.validateForm = this.formBuilder.group({
      id: ['', [Validators.required]],
      url: ['', [Validators.required]],
      snapshot: [false, [Validators.required]],
      username: '',
      password: '',
      proxyProtocol: 'HTTP',
      proxyHost: '',
      proxyPort: [
        null,
        [Validators.pattern('^()([1-9]|[1-5]?[0-9]{2,4}|6[1-4][0-9]{3}|65[1-4][0-9]{2}|655[1-2][0-9]|6553[1-5])$')]
      ],
      proxyLogin: '',
      proxyPassword: ''
    });
  }
}
