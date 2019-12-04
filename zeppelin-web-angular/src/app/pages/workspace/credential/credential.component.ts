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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { collapseMotion, NzMessageService } from 'ng-zorro-antd';

import { finalize } from 'rxjs/operators';

import { CredentialForm } from '@zeppelin/interfaces';
import { CredentialService, InterpreterService, TicketService } from '@zeppelin/services';

@Component({
  selector: 'zeppelin-credential',
  templateUrl: './credential.component.html',
  styleUrls: ['./credential.component.less'],
  animations: [collapseMotion],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CredentialComponent implements OnInit {
  addForm: FormGroup;
  showAdd = false;
  adding = false;
  interpreterNames: string[] = [];
  interpreterFilteredNames: string[] = [];
  editFlags: Map<string, CredentialForm> = new Map();
  credentialFormArray: FormArray = this.fb.array([]);
  docsLink: string;

  get credentialControls(): FormGroup[] {
    return this.credentialFormArray.controls as FormGroup[];
  }

  constructor(
    private cdr: ChangeDetectorRef,
    private fb: FormBuilder,
    private nzMessageService: NzMessageService,
    private interpreterService: InterpreterService,
    private credentialService: CredentialService,
    private ticketService: TicketService
  ) {
    this.setDocsLink();
  }

  setDocsLink() {
    const version = this.ticketService.version;
    this.docsLink = `https://zeppelin.apache.org/docs/${version}/setup/security/datasource_authorization.html`;
  }

  onEntityInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input && input.value) {
      this.interpreterFilteredNames = this.interpreterNames
        .filter(e => e.indexOf(input.value.trim()) !== -1)
        .slice(0, 10);
    } else {
      this.interpreterFilteredNames = this.interpreterNames.slice(0, 10);
    }
  }

  getEntityFromForm(form: FormGroup): string {
    return form.get('entity') && form.get('entity').value;
  }

  isEditing(form: FormGroup): boolean {
    const entity = this.getEntityFromForm(form);
    return entity && this.editFlags.has(entity);
  }

  setEditable(form: FormGroup) {
    const entity = this.getEntityFromForm(form);
    if (entity) {
      this.editFlags.set(entity, form.getRawValue());
    }
    this.cdr.markForCheck();
  }

  unsetEditable(form: FormGroup, reset = true) {
    const entity = this.getEntityFromForm(form);
    if (reset && entity && this.editFlags.has(entity)) {
      form.reset(this.editFlags.get(entity));
    }
    this.editFlags.delete(entity);
    this.cdr.markForCheck();
  }

  submitForm(): void {
    for (const i in this.addForm.controls) {
      this.addForm.controls[i].markAsDirty();
      this.addForm.controls[i].updateValueAndValidity();
    }
    if (this.addForm.valid) {
      const data = this.addForm.getRawValue() as CredentialForm;
      this.addCredential(data);
    }
  }

  saveCredential(form: FormGroup) {
    for (const i in form.controls) {
      form.controls[i].markAsDirty();
      form.controls[i].updateValueAndValidity();
    }
    if (form.valid) {
      this.credentialService.updateCredential(form.getRawValue()).subscribe(() => {
        this.nzMessageService.success('Successfully saved credentials.');
        this.unsetEditable(form, false);
      });
    }
  }

  removeCredential(form: FormGroup) {
    const entity = this.getEntityFromForm(form);
    if (entity) {
      this.credentialService.removeCredential(entity).subscribe(() => {
        this.getCredentials();
      });
    }
  }

  triggerAdd(): void {
    this.showAdd = !this.showAdd;
    this.cdr.markForCheck();
  }

  cancelAdd() {
    this.showAdd = false;
    this.resetAddForm();
    this.cdr.markForCheck();
  }

  getCredentials() {
    this.credentialService.getCredentials().subscribe(data => {
      const controls = [...Object.entries(data.userCredentials)].map(e => {
        const entity = e[0];
        const { username, password } = e[1];
        return this.fb.group({
          entity: [entity, [Validators.required]],
          username: [username, [Validators.required]],
          password: [password, [Validators.required]]
        });
      });
      this.credentialFormArray = this.fb.array(controls);
      this.cdr.markForCheck();
    });
  }

  getInterpreterNames() {
    this.interpreterService.getInterpretersSetting().subscribe(data => {
      this.interpreterNames = data.map(e => `${e.group}.${e.name}`);
      this.interpreterFilteredNames = this.interpreterNames.slice(0, 10);
      this.cdr.markForCheck();
    });
  }

  addCredential(data: CredentialForm) {
    this.adding = true;
    this.cdr.markForCheck();
    this.credentialService
      .addCredential(data)
      .pipe(
        finalize(() => {
          this.adding = false;
          this.cdr.markForCheck();
        })
      )
      .subscribe(() => {
        this.nzMessageService.success('Successfully saved credentials.');
        this.getCredentials();
        this.resetAddForm();
        this.cdr.markForCheck();
      });
  }

  resetAddForm() {
    this.addForm.reset({
      entity: null,
      username: null,
      password: null
    });
    this.cdr.markForCheck();
  }

  ngOnInit(): void {
    this.getCredentials();
    this.getInterpreterNames();
    this.addForm = this.fb.group({
      entity: [null, [Validators.required]],
      username: [null, [Validators.required]],
      password: [null, [Validators.required]]
    });
  }
}
