import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy } from '@angular/core';
import { editor, IDisposable, Range } from 'monaco-editor';
import { NzModalRef } from 'ng-zorro-antd';
import {
  defaultTemplateUpdaterRules,
  LogLevel,
  Message,
  MessageDetail,
  TemplateUpdater,
  ValueChangeRule
} from 'ng1-template-updater';
import { combineLatest, Subject } from 'rxjs';
import IEditor = editor.IEditor;
import ITextModel = editor.ITextModel;
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

const zeppelinFunctionChangeRule: ValueChangeRule = (expression: string, start?: number) => {
  let value = expression;
  const messages: Message[] = [];
  const funChanges = [
    {
      regexp: /z\.angularBind/gm,
      replace: 'z.set'
    },
    {
      regexp: /z\.angularUnbind/gm,
      replace: 'z.unset'
    },
    {
      regexp: /z\.runParagraph/gm,
      replace: 'z.run'
    }
  ];

  funChanges.forEach(change => {
    let match = change.regexp.exec(value);
    while (match !== null) {
      messages.push({
        position: start + match.index,
        message: `${match[0]} has been deprecated, using ${change.replace} instead`,
        length: match[0].length,
        // url: 'https://angular.io/guide/ajs-quick-reference',
        level: LogLevel.Info
      });
      match = change.regexp.exec(value);
    }
    value = value.replace(change.regexp, change.replace);
  });

  return {
    messages,
    value
  };
};

@Component({
  selector: 'zeppelin-ng1-migration',
  templateUrl: './ng1-migration.component.html',
  styleUrls: ['./ng1-migration.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Ng1MigrationComponent implements OnDestroy {
  @Input() origin: string;
  @Input() index: number;
  @Input() match: string;
  @Input() template: string;

  messageDetails: MessageDetail[] = [];
  templateUpdater: TemplateUpdater;
  errorCount = 0;
  decorations: string[] = [];
  timeoutId = -1;
  editor: IStandaloneCodeEditor;
  editorModel: ITextModel;
  editorInit$ = new Subject();
  editorChangeDisposable: IDisposable;

  constructor(private nzModalRef: NzModalRef, private cdr: ChangeDetectorRef) {
    const updateRules = {
      ...defaultTemplateUpdaterRules,
      valueChangeRules: [...defaultTemplateUpdaterRules.valueChangeRules, zeppelinFunctionChangeRule]
    };
    this.templateUpdater = new TemplateUpdater(updateRules);
    combineLatest([this.nzModalRef.afterOpen, this.editorInit$]).subscribe(() => {
      if (this.editor) {
        this.editorModel = this.editor.getModel() as ITextModel;
        this.editor.setValue(this.template);
        this.editor.layout();
        this.bindEditorEvents();
        this.check();
        setTimeout(() => {
          this.editor.focus();
        }, 150);
      }
    });
  }

  onEditorInit(_editor: IEditor) {
    this.editorInit$.next();
    this.editorInit$.complete();
    this.editor = _editor as IStandaloneCodeEditor;
  }

  bindEditorEvents() {
    if (this.editorModel) {
      this.editorChangeDisposable = this.editorModel.onDidChangeContent(() => {
        clearTimeout(this.timeoutId);
        this.timeoutId = setTimeout(() => {
          this.check();
        }, 300);
      });
    }
  }

  scrollToLine(failure: MessageDetail) {
    const line = failure.pos.line + 1;
    const character = failure.pos.character + 1;
    const range = new Range(line, character, line, character + failure.length);
    this.editor.revealRangeAtTop(range);
    this.editor.setSelection(range);
    this.editor.focus();
  }

  check() {
    const code = this.editor.getValue();
    const { messages } = this.templateUpdater.parse(code);
    this.messageDetails = [...messages];
    this.errorCount = messages.filter(f => f.level === LogLevel.Error).length;
    this.decorations = this.editor.deltaDecorations(
      this.decorations,
      messages.map(failure => {
        const line = failure.pos.line + 1;
        const character = failure.pos.character + 1;
        return {
          range: new Range(line, character, line, character + failure.length),
          options: {
            className: failure.level === LogLevel.Error ? '' : 'warn-content',
            inlineClassName: failure.level === LogLevel.Error ? 'decoration-link' : '',
            stickiness: 1,
            hoverMessage: {
              value: failure.message + (failure.url ? ` [more](${failure.url})` : '')
            }
          }
        };
      })
    );
    this.cdr.markForCheck();
  }

  fix() {
    const code = this.editor.getValue();
    const { template } = this.templateUpdater.parse(code);
    this.editor.setValue(template);
  }

  updateAndCopy() {
    const code = this.editor.getValue();
    const newTemplate = this.origin.replace(this.match, `%ng\n${code}`);
    this.nzModalRef.close(newTemplate);
  }

  cancel() {
    this.nzModalRef.destroy();
  }

  ngOnDestroy(): void {
    if (this.editorChangeDisposable) {
      this.editorChangeDisposable.dispose();
    }
    if (this.editorModel) {
      this.editorModel.dispose();
    }
  }
}
