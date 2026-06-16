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
import { AbstractControl, FormArray, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { DestroyHookComponent } from '@zeppelin/core';
import {
  Interpreter,
  InterpreterPropertyTypes,
  InterpreterPropertyValue,
  InterpreterSettingRequest
} from '@zeppelin/interfaces';
import { InterpreterService, SecurityService, TicketService } from '@zeppelin/services';
import { BehaviorSubject, Observable } from 'rxjs';
import { debounceTime, filter, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { InterpreterComponent } from '../interpreter.component';

type PropertyValue = string | number | boolean | null;

interface PropertyFormGroup {
  key: FormControl<string>;
  value: FormControl<PropertyValue>;
  description: FormControl<string | null>;
  type: FormControl<InterpreterPropertyTypes>;
}

interface DependencyFormGroup {
  groupArtifactVersion: FormControl<string>;
  exclusions: FormControl<string>;
}

interface OptionFormGroup {
  isExistingProcess: FormControl<boolean>;
  isUserImpersonate: FormControl<boolean>;
  owners: FormControl<string[]>;
  perNote: FormControl<string>;
  perUser: FormControl<string>;
  port: FormControl<number | null>;
  host: FormControl<string>;
  remote: FormControl<boolean>;
  setPermission: FormControl<boolean>;
  // TODO: `session`/`process` are write-only leftovers from the pre-0.7 boolean isolation
  // model, superseded by the perNote/perUser modes in ZEPPELIN-1210. They are never read
  // by the template, never sent to the server, and should be removed in a follow-up.
  session: FormControl<boolean>;
  process: FormControl<boolean>;
}

interface InterpreterFormGroup {
  name: FormControl<string>;
  group: FormControl<string>;
  option: FormGroup<OptionFormGroup>;
  properties: FormArray<FormGroup<PropertyFormGroup>>;
  dependencies: FormArray<FormGroup<DependencyFormGroup>>;
}

@Component({
  selector: 'zeppelin-interpreter-item',
  templateUrl: './item.component.html',
  styleUrls: ['./item.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false
})
export class InterpreterItemComponent extends DestroyHookComponent implements OnInit, OnDestroy {
  @Input() mode: 'create' | 'view' | 'edit' = 'view';
  @Input() interpreter?: Interpreter;

  formGroup!: FormGroup<InterpreterFormGroup>;
  optionFormGroup!: FormGroup<OptionFormGroup>;
  editingPropertiesFormGroup?: FormGroup<PropertyFormGroup>;
  editingDependenceFormGroup?: FormGroup<DependencyFormGroup>;
  propertiesFormArray!: FormArray<FormGroup<PropertyFormGroup>>;
  dependenciesFormArray!: FormArray<FormGroup<DependencyFormGroup>>;
  userList$?: Observable<string[]>;
  userSearchChange$: BehaviorSubject<string> | null = new BehaviorSubject('');
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
    if (!this.interpreter) {
      throw new Error("'interpreter' is not defined. Please check if it is initialized properly.");
    }
    this.parent.restartInterpreterSetting(this.interpreter.name);
  }

  handleRemove() {
    if (!this.interpreter) {
      throw new Error("'interpreter' is not defined. Please check if it is initialized properly.");
    }
    this.parent.removeInterpreterSetting(this.interpreter.name);
  }

  handleSave() {
    this.addProperties();
    this.addDependence();
    const formData = this.formGroup.getRawValue();
    const properties: Record<string, InterpreterPropertyValue> = {};

    formData.properties.forEach(({ key, value, type }) => {
      properties[key] = {
        // Numeric inputs hold JS numbers, which Gson would deserialize as Double
        // (e.g. 60000 -> "60000.0") and break Long/Integer parsing (ZEPPELIN-6395),
        // so send them as strings. Checkboxes stay real booleans, as in the classic UI.
        value: type === 'checkbox' ? value === true || value === 'true' : String(value ?? ''),
        type,
        name: key
      };
    });

    // session/process are UI-only state derived from perNote/perUser;
    // the server-side InterpreterOption has no such fields, so they are not sent.
    const { isExistingProcess, isUserImpersonate, owners, perNote, perUser, port, host, remote, setPermission } =
      formData.option;
    const setting: InterpreterSettingRequest = {
      name: formData.name,
      group: formData.group,
      option: { isExistingProcess, isUserImpersonate, owners, perNote, perUser, port, host, remote, setPermission },
      properties,
      dependencies: formData.dependencies.map(({ groupArtifactVersion, exclusions }) => ({
        groupArtifactVersion,
        exclusions: exclusions
          .split(',')
          .map(s => s.trim())
          .filter(s => s !== '')
      }))
    };

    if (this.mode === 'create') {
      this.parent.addInterpreterSetting(setting);
    } else {
      this.parent.updateInterpreter(setting);
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
    if (!this.userSearchChange$) {
      throw new Error("'userSearchChange$' is not defined. Calling it after component is destroyed is not allowed.");
    }
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

  onTypeChange(type: InterpreterPropertyTypes) {
    if (!this.editingPropertiesFormGroup) {
      throw new Error("'editingPropertiesFormGroup' is not defined. Please check if it is initialized properly.");
    }
    let valueSet: PropertyValue;
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
    this.editingPropertiesFormGroup.controls.value.setValue(valueSet);
  }

  addDependence(): void {
    if (!this.editingDependenceFormGroup) {
      throw new Error("'editingDependenceFormGroup' is not defined. Please check if it is initialized properly.");
    }
    this.editingDependenceFormGroup.updateValueAndValidity();
    if (this.editingDependenceFormGroup.valid) {
      const data = this.editingDependenceFormGroup.getRawValue();
      const current = this.dependenciesFormArray.controls.find(
        control => control.controls.groupArtifactVersion.value === data.groupArtifactVersion
      );
      if (current) {
        current.controls.exclusions.setValue(data.exclusions);
      } else {
        this.dependenciesFormArray.push(this.createDependencyFormGroup(data));
      }
      this.editingDependenceFormGroup.reset({
        exclusions: '',
        groupArtifactVersion: ''
      });
    }
  }

  addProperties(): void {
    if (!this.editingPropertiesFormGroup) {
      throw new Error("'editingPropertiesFormGroup' is not defined. Please check if it is initialized properly.");
    }
    this.editingPropertiesFormGroup.updateValueAndValidity();
    if (this.editingPropertiesFormGroup.valid) {
      const data = this.editingPropertiesFormGroup.getRawValue();

      const current = this.propertiesFormArray.controls.find(control => control.controls.key.value === data.key);
      if (current) {
        current.controls.value.setValue(data.value);
        current.controls.type.setValue(data.type);
      } else {
        this.propertiesFormArray.push(this.createPropertyFormGroup({ ...data, value: data.value ?? '' }));
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

    this.optionFormGroup.controls.perNote.setValue(perNote);
    this.optionFormGroup.controls.perUser.setValue(perUser);

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

    this.optionFormGroup.controls.perNote.setValue(sharedModeName);
    this.optionFormGroup.controls.perUser.setValue(sharedModeName);
    this.interpreterRunningOption = globallyModeName;
  }

  setPerNoteOrUserOption(type: 'perNote' | 'perUser', value: string) {
    this.optionFormGroup.controls[type].setValue(value);
    switch (value) {
      case this.sessionOptionMap.isolated:
        this.optionFormGroup.controls.session.setValue(false);
        this.optionFormGroup.controls.process.setValue(true);
        break;
      case this.sessionOptionMap.scoped:
        this.optionFormGroup.controls.session.setValue(true);
        this.optionFormGroup.controls.process.setValue(false);
        break;
      case this.sessionOptionMap.shared:
        this.optionFormGroup.controls.session.setValue(false);
        this.optionFormGroup.controls.process.setValue(false);
        break;
    }
  }

  nameValidator(control: AbstractControl<string>): ValidationErrors | null {
    if (this.mode !== 'create') {
      return null;
    }
    const name = control.value.trim();
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
    this.optionFormGroup = new FormGroup<OptionFormGroup>({
      isExistingProcess: new FormControl(false, { nonNullable: true }),
      isUserImpersonate: new FormControl(false, { nonNullable: true }),
      owners: new FormControl<string[]>([], { nonNullable: true }),
      perNote: new FormControl('', { nonNullable: true }),
      perUser: new FormControl('', { nonNullable: true }),
      port: new FormControl<number | null>(null, [
        Validators.pattern('^()([1-9]|[1-5]?[0-9]{2,4}|6[1-4][0-9]{3}|65[1-4][0-9]{2}|655[1-2][0-9]|6553[1-5])$')
      ]),
      host: new FormControl('', { nonNullable: true }),
      remote: new FormControl(true, { nonNullable: true }),
      setPermission: new FormControl(false, { nonNullable: true }),
      session: new FormControl(false, { nonNullable: true }),
      process: new FormControl(false, { nonNullable: true })
    });

    this.propertiesFormArray = new FormArray<FormGroup<PropertyFormGroup>>([]);
    this.dependenciesFormArray = new FormArray<FormGroup<DependencyFormGroup>>([]);

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
        const exclusions = Array.isArray(e.exclusions) ? e.exclusions : [];
        this.dependenciesFormArray.push(
          this.createDependencyFormGroup({
            exclusions: exclusions.join(','),
            groupArtifactVersion: e.groupArtifactVersion
          })
        );
      });

      // set properties fields
      Object.entries(this.interpreter.properties).forEach(([key, item]) => {
        this.propertiesFormArray.push(
          this.createPropertyFormGroup({
            key,
            value: item.value,
            description: null,
            type: item.type
          })
        );
      });
    }

    this.formGroup = new FormGroup<InterpreterFormGroup>({
      name: new FormControl(name, {
        nonNullable: true,
        validators: [Validators.required, control => this.nameValidator(control)]
      }),
      group: new FormControl(group, { nonNullable: true, validators: [Validators.required] }),
      option: this.optionFormGroup,
      properties: this.propertiesFormArray,
      dependencies: this.dependenciesFormArray
    });
  }

  setupEditableForm(): void {
    if (!this.userSearchChange$) {
      throw new Error("'userSearchChange$' is not defined. Calling it after component is destroyed is not allowed.");
    }
    this.userList$ = this.userSearchChange$.pipe(
      debounceTime(500),
      filter(value => !!value),
      switchMap(value => this.securityService.searchUsers(value)),
      map(data => data.users),
      tap(() => {
        this.cdr.markForCheck();
      })
    );

    this.editingPropertiesFormGroup = this.createPropertyFormGroup({
      key: '',
      value: '',
      description: null,
      type: 'string'
    });

    this.editingDependenceFormGroup = this.createDependencyFormGroup({
      groupArtifactVersion: '',
      exclusions: ''
    });

    if (this.mode === 'create') {
      this.formGroup.controls.group.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(value => {
        // remove all controls
        while (this.propertiesFormArray.length) {
          this.propertiesFormArray.removeAt(0);
        }

        const interpreters = this.parent.availableInterpreters.filter(e => e.group === value);
        interpreters.forEach(interpreter => {
          Object.entries(interpreter.properties).forEach(([key, item]) => {
            this.propertiesFormArray.push(
              this.createPropertyFormGroup({
                key,
                value: item.defaultValue ?? '',
                description: item.description ?? null,
                type: item.type
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
    this.userSearchChange$?.complete();
    this.userSearchChange$ = null;
    super.ngOnDestroy();
  }

  private createPropertyFormGroup(property: {
    key: string;
    value: PropertyValue;
    description: string | null;
    type: InterpreterPropertyTypes;
  }): FormGroup<PropertyFormGroup> {
    return new FormGroup<PropertyFormGroup>({
      key: new FormControl(property.key, { nonNullable: true, validators: [Validators.required] }),
      value: new FormControl<PropertyValue>(property.value),
      description: new FormControl(property.description),
      type: new FormControl(property.type, { nonNullable: true })
    });
  }

  private createDependencyFormGroup(dependency: {
    groupArtifactVersion: string;
    exclusions: string;
  }): FormGroup<DependencyFormGroup> {
    return new FormGroup<DependencyFormGroup>({
      groupArtifactVersion: new FormControl(dependency.groupArtifactVersion, {
        nonNullable: true,
        validators: [Validators.required]
      }),
      exclusions: new FormControl(dependency.exclusions, { nonNullable: true })
    });
  }
}
