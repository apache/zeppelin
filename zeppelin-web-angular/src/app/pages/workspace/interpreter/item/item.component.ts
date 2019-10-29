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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { DestroyHookComponent } from '@zeppelin/core';
import { Interpreter } from '@zeppelin/interfaces';
import { InterpreterService, SecurityService, TicketService } from '@zeppelin/services';
import { BehaviorSubject, Observable } from 'rxjs';
import { debounceTime, filter, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { InterpreterComponent } from '../interpreter.component';

@Component({
  selector: 'zeppelin-interpreter-item',
  templateUrl: './item.component.html',
  styleUrls: ['./item.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InterpreterItemComponent extends DestroyHookComponent implements OnInit, OnDestroy {
  @Input() mode: 'create' | 'view' | 'edit' = 'view';
  @Input() interpreter: Interpreter;

  formGroup: FormGroup;
  optionFormGroup: FormGroup;
  editingPropertiesFormGroup: FormGroup;
  editingDependenceFormGroup: FormGroup;
  propertiesFormArray: FormArray;
  dependenciesFormArray: FormArray;
  userList$: Observable<string[]>;
  userSearchChange$ = new BehaviorSubject('');
  runningOptionMap = {
    sharedModeName: 'shared',
    globallyModeName: 'Globally',
    perNoteModeName: 'Per Note',
    perUserModeName: 'Per User'
  };

  sessionOptionMap = {
    isolated: 'isolated',
    scoped: 'scoped',
    shared: 'shared'
  };

  interpreterRunningOption = 'Globally';

  switchToEditMode(): void {
    this.setupEditableForm();
    this.formGroup.enable();
    this.mode = 'edit';
    this.cdr.markForCheck();
  }

  handleRestart() {
    this.parent.restartInterpreterSetting(this.interpreter.name);
  }

  handleRemove() {
    this.parent.removeInterpreterSetting(this.interpreter.name);
  }

  handleSave() {
    const formData = this.formGroup.getRawValue();
    const properties = {};

    formData.properties
      .sort(e => e.key)
      .forEach(e => {
        const { key, value, type } = e;
        properties[key] = {
          value,
          type,
          name: key
        };
      });
    formData.properties = properties;

    formData.dependencies.forEach(e => {
      e.exclusions = e.exclusions.split(',').filter(s => s !== '');
    });

    if (this.mode === 'create') {
      this.parent.addInterpreterSetting(formData);
    } else {
      this.parent.updateInterpreter(formData);
      this.mode = 'view';
    }
  }

  handleCancel() {
    if (this.mode === 'create') {
      this.parent.showCreateSetting = false;
    } else {
      this.mode = 'view';
      this.buildForm();
      this.formGroup.disable();
    }
  }

  interpretersTrackFn(_: number, item: Interpreter) {
    return item.name;
  }

  onUserSearch(value: string): void {
    this.userSearchChange$.next(value);
  }

  removeProperty(index: number): void {
    this.propertiesFormArray.removeAt(index);
    this.cdr.markForCheck();
  }

  removeDependence(index: number): void {
    this.dependenciesFormArray.removeAt(index);
    this.cdr.markForCheck();
  }

  onTypeChange(type: string) {
    let valueSet: string | boolean | number;
    switch (type) {
      case 'number':
        valueSet = 0;
        break;
      case 'checkbox':
        valueSet = false;
        break;
      default:
        valueSet = '';
    }
    this.editingPropertiesFormGroup.get('value').setValue(valueSet);
  }

  addDependence(): void {
    this.editingDependenceFormGroup.updateValueAndValidity();
    if (this.editingDependenceFormGroup.valid) {
      const data = this.editingDependenceFormGroup.getRawValue();
      const current = this.dependenciesFormArray.controls.find(
        control => control.get('groupArtifactVersion').value === data.groupArtifactVersion
      );
      if (current) {
        current.get('exclusions').setValue(data.exclusions);
      } else {
        this.dependenciesFormArray.push(
          this.formBuilder.group({
            groupArtifactVersion: [data.groupArtifactVersion, [Validators.required]],
            exclusions: data.exclusions
          })
        );
      }
      this.editingDependenceFormGroup.reset({
        exclusions: '',
        groupArtifactVersion: ''
      });
    }
  }

  addProperties(): void {
    this.editingPropertiesFormGroup.updateValueAndValidity();
    if (this.editingPropertiesFormGroup.valid) {
      const data = this.editingPropertiesFormGroup.getRawValue();

      const current = this.propertiesFormArray.controls.find(control => control.get('key').value === data.key);
      if (current) {
        current.get('value').setValue(data.value);
        current.get('type').setValue(data.type);
      } else {
        this.propertiesFormArray.push(
          this.formBuilder.group({
            key: [data.key, [Validators.required]],
            value: data.value || '',
            description: null,
            type: data.type
          })
        );
      }
      this.editingPropertiesFormGroup.reset({
        key: '',
        value: '',
        description: null,
        type: 'string'
      });
    }
  }

  setInterpreterRunningOption(perNote: string, perUser: string) {
    const { sharedModeName, globallyModeName, perNoteModeName, perUserModeName } = this.runningOptionMap;

    this.optionFormGroup.get('perNote').setValue(perNote);
    this.optionFormGroup.get('perUser').setValue(perUser);

    // Globally == shared_perNote + shared_perUser
    if (perNote === sharedModeName && perUser === sharedModeName) {
      this.interpreterRunningOption = globallyModeName;
      return;
    }

    const ticket = this.ticketService.originTicket;

    if (ticket.ticket === 'anonymous' && ticket.roles === '[]') {
      if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
        this.interpreterRunningOption = perNoteModeName;
        return;
      }
    } else if (ticket.ticket !== 'anonymous') {
      if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
        if (perUser !== undefined && typeof perUser === 'string' && perUser !== '') {
          this.interpreterRunningOption = perUserModeName;
          return;
        }
        this.interpreterRunningOption = perNoteModeName;
        return;
      }
    }

    this.optionFormGroup.get('perNote').setValue(sharedModeName);
    this.optionFormGroup.get('perUser').setValue(sharedModeName);
    this.interpreterRunningOption = globallyModeName;
  }

  setPerNoteOrUserOption(type: 'perNote' | 'perUser', value: string) {
    this.optionFormGroup.get(type).setValue(value);
    switch (value) {
      case this.sessionOptionMap.isolated:
        this.optionFormGroup.get('session').setValue(false);
        this.optionFormGroup.get('process').setValue(true);
        break;
      case this.sessionOptionMap.scoped:
        this.optionFormGroup.get('session').setValue(true);
        this.optionFormGroup.get('process').setValue(false);
        break;
      case this.sessionOptionMap.shared:
        this.optionFormGroup.get('session').setValue(false);
        this.optionFormGroup.get('process').setValue(false);
        break;
    }
  }

  nameValidator(control: AbstractControl): ValidationErrors | null {
    if (this.mode !== 'create') {
      return null;
    }
    const name = (control.value as string).trim();
    const exist = this.parent.interpreterSettings.find(e => e.name === name);
    if (exist) {
      return { exist: true, message: `Name '${name}' already exists` };
    } else {
      return null;
    }
  }

  buildForm(): void {
    let name = '';
    let group = '';
    this.optionFormGroup = this.formBuilder.group({
      isExistingProcess: false,
      isUserImpersonate: false,
      owners: [[]],
      perNote: '',
      perUser: '',
      port: [
        null,
        [Validators.pattern('^()([1-9]|[1-5]?[0-9]{2,4}|6[1-4][0-9]{3}|65[1-4][0-9]{2}|655[1-2][0-9]|6553[1-5])$')]
      ],
      host: '',
      remote: true,
      setPermission: false,
      session: false,
      process: false
    });

    this.propertiesFormArray = this.formBuilder.array([]);
    this.dependenciesFormArray = this.formBuilder.array([]);

    if (this.mode === 'view' && this.interpreter) {
      name = this.interpreter.name;
      group = this.interpreter.group;

      // set option fields
      this.optionFormGroup.reset({
        ...this.interpreter.option,
        port: this.interpreter.option.port === -1 ? null : this.interpreter.option.port
      });

      // set dependencies fields
      this.interpreter.dependencies.forEach(e => {
        this.dependenciesFormArray.push(
          this.formBuilder.group({
            exclusions: [e.exclusions.join(',')],
            groupArtifactVersion: [e.groupArtifactVersion, [Validators.required]]
          })
        );
      });

      // set properties fields
      Object.keys(this.interpreter.properties).forEach(key => {
        const item = this.interpreter.properties[key];
        this.propertiesFormArray.push(
          this.formBuilder.group({
            key: key,
            value: item.value,
            description: null,
            type: item.type
          })
        );
      });
    }

    this.formGroup = this.formBuilder.group({
      name: [name, [Validators.required, c => this.nameValidator(c)]],
      group: [group, [Validators.required]],
      option: this.optionFormGroup,
      properties: this.propertiesFormArray,
      dependencies: this.dependenciesFormArray
    });
  }

  setupEditableForm(): void {
    this.userList$ = this.userSearchChange$.pipe(
      debounceTime(500),
      filter(value => !!value),
      switchMap(value => this.securityService.searchUsers(value)),
      map(data => data.users),
      tap(() => {
        this.cdr.markForCheck();
      })
    );

    this.editingPropertiesFormGroup = this.formBuilder.group({
      key: ['', [Validators.required]],
      value: '',
      description: null,
      type: 'string'
    });

    this.editingDependenceFormGroup = this.formBuilder.group({
      groupArtifactVersion: ['', [Validators.required]],
      exclusions: ['']
    });

    if (this.mode === 'create') {
      this.formGroup
        .get('group')
        .valueChanges.pipe(takeUntil(this.destroy$))
        .subscribe(value => {
          // remove all controls
          while (this.propertiesFormArray.length) {
            this.propertiesFormArray.removeAt(0);
          }

          const interpreters = this.parent.availableInterpreters.filter(e => e.group === value);
          interpreters.forEach(interpreter => {
            Object.keys(interpreter.properties).forEach(key => {
              this.propertiesFormArray.push(
                this.formBuilder.group({
                  key: [key, [Validators.required]],
                  value: interpreter.properties[key].defaultValue,
                  description: interpreter.properties[key].description,
                  type: interpreter.properties[key].type
                })
              );
            });
          });
          this.cdr.markForCheck();
        });
    }
  }

  constructor(
    public parent: InterpreterComponent,
    public ticketService: TicketService,
    private securityService: SecurityService,
    private interpreterService: InterpreterService,
    private formBuilder: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {
    super();
  }

  ngOnInit() {
    this.buildForm();
    const option = this.optionFormGroup.getRawValue();
    this.setInterpreterRunningOption(option.perNote, option.perUser);

    if (this.mode !== 'view') {
      this.setupEditableForm();
      this.formGroup.enable();
    } else {
      this.formGroup.disable();
    }
  }

  ngOnDestroy(): void {
    this.userSearchChange$.complete();
    this.userSearchChange$ = null;
    super.ngOnDestroy();
  }
}
