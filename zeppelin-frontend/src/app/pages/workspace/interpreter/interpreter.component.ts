import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

import { collapseMotion, NzMessageService, NzModalService } from 'ng-zorro-antd';

import { Interpreter, InterpreterPropertyTypes, InterpreterRepository } from '@zeppelin/interfaces';
import { InterpreterService } from '@zeppelin/services';

import { InterpreterCreateRepositoryModalComponent } from './create-repository-modal/create-repository-modal.component';

@Component({
  selector: 'zeppelin-interpreter',
  templateUrl: './interpreter.component.html',
  styleUrls: ['./interpreter.component.less'],
  animations: [collapseMotion],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InterpreterComponent implements OnInit, OnDestroy {
  searchInterpreter = '';
  search$ = new Subject<string>();
  showRepository = false;
  showCreateSetting = false;
  propertyTypes: InterpreterPropertyTypes[] = [];
  interpreterSettings: Interpreter[] = [];
  repositories: InterpreterRepository[] = [];
  availableInterpreters: Interpreter[] = [];
  filteredInterpreterSettings: Interpreter[] = [];

  onSearchChange(value: string) {
    this.search$.next(value);
  }

  filterInterpreters(value: string) {
    this.filteredInterpreterSettings = this.interpreterSettings.filter(e => e.name.search(value) !== -1);
    this.cdr.markForCheck();
  }

  triggerRepository(): void {
    this.showRepository = !this.showRepository;
    this.cdr.markForCheck();
  }

  removeRepository(repo: InterpreterRepository): void {
    this.nzModalService.confirm({
      nzTitle: repo.id,
      nzContent: 'Do you want to delete this repository?',
      nzOnOk: () => {
        this.interpreterService.removeRepository(repo.id).subscribe(() => {
          this.repositories = this.repositories.filter(e => e.id !== repo.id);
          this.cdr.markForCheck();
        });
      }
    });
  }

  addInterpreterSetting(data: Interpreter): void {
    this.interpreterService.addInterpreterSetting(data).subscribe(res => {
      this.interpreterSettings.push(res);
      this.showCreateSetting = false;
      this.filterInterpreters(this.searchInterpreter);
      this.cdr.markForCheck();
    });
  }

  updateInterpreter(data: Interpreter): void {
    this.interpreterService.updateInterpreter(data).subscribe(res => {
      const current = this.interpreterSettings.find(e => e.name === res.name);
      if (current) {
        current.status = res.status;
        current.errorReason = res.errorReason;
        current.option = res.option;
        current.properties = res.properties;
        current.dependencies = res.dependencies;
      }
      this.filterInterpreters(this.searchInterpreter);
      this.cdr.markForCheck();
    });
  }

  removeInterpreterSetting(settingId: string): void {
    this.nzModalService.confirm({
      nzTitle: 'Remove Interpreter',
      nzContent: 'Do you want to delete this interpreter setting?',
      nzOnOk: () => {
        this.interpreterService.removeInterpreterSetting(settingId).subscribe(() => {
          const index = this.interpreterSettings.findIndex(e => e.name === settingId);
          this.interpreterSettings.splice(index, 1);
          this.filterInterpreters(this.searchInterpreter);
          this.cdr.markForCheck();
        });
      }
    });
  }

  restartInterpreterSetting(settingId: string): void {
    this.nzModalService.confirm({
      nzTitle: 'Restart Interpreter',
      nzContent: 'Do you want to restart this interpreter?',
      nzOnOk: () => {
        this.interpreterService.restartInterpreterSetting(settingId).subscribe(() => {
          this.nzMessageService.info('Interpreter stopped. Will be lazily started on next run.');
        });
      }
    });
  }

  createRepository(): void {
    const modalRef = this.nzModalService.create({
      nzTitle: 'Add New Repository',
      nzContent: InterpreterCreateRepositoryModalComponent,
      nzFooter: null,
      nzWidth: '600px'
    });
    modalRef.afterClose.subscribe(data => {
      if (data === 'Done') {
        this.getRepositories();
      }
    });
  }

  getPropertyTypes(): void {
    this.interpreterService.getAvailableInterpreterPropertyTypes().subscribe(data => {
      this.propertyTypes = data;
      this.cdr.markForCheck();
    });
  }

  getInterpreterSettings(): void {
    this.interpreterService.getInterpretersSetting().subscribe(data => {
      this.interpreterSettings = data;
      this.filteredInterpreterSettings = data;
      this.cdr.markForCheck();
    });
  }

  getAvailableInterpreters(): void {
    this.interpreterService.getAvailableInterpreters().subscribe(data => {
      this.availableInterpreters = Object.keys(data)
        .sort()
        .map(key => data[key]);
      this.cdr.markForCheck();
    });
  }

  getRepositories(): void {
    this.interpreterService.getRepositories().subscribe(data => {
      this.repositories = data;
      this.cdr.markForCheck();
    });
  }

  constructor(
    private interpreterService: InterpreterService,
    private cdr: ChangeDetectorRef,
    private nzModalService: NzModalService,
    private nzMessageService: NzMessageService
  ) {}

  ngOnInit() {
    this.getPropertyTypes();
    this.getInterpreterSettings();
    this.getAvailableInterpreters();
    this.getRepositories();

    this.search$.pipe(debounceTime(150)).subscribe(value => this.filterInterpreters(value));
  }

  ngOnDestroy(): void {
    this.search$.next();
    this.search$.complete();
    this.search$ = null;
  }
}
