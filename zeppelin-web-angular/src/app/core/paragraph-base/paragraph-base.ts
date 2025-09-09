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

import { ChangeDetectorRef } from '@angular/core';
import {
  AngularObjectRemove,
  AngularObjectUpdate,
  GraphConfig,
  Message,
  MessageReceiveDataTypeMap,
  OP,
  ParagraphConfig,
  ParagraphConfigResult,
  ParagraphConfigResults,
  ParagraphEditorSetting,
  ParagraphItem,
  ParagraphIResultsMsgItem,
  ParagraphResults
} from '@zeppelin/sdk';

import * as DiffMatchPatch from 'diff-match-patch';
import { isEmpty, isEqual } from 'lodash';

import { MessageListener, MessageListenersManager } from '../message-listener/message-listener';
import { AngularContextManager } from './angular-context-manager';
import { NoteStatus } from './note-status';

export const ParagraphStatus = {
  READY: 'READY',
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  FINISHED: 'FINISHED',
  ABORT: 'ABORT',
  ERROR: 'ERROR'
};

export abstract class ParagraphBase extends MessageListenersManager {
  paragraph?: ParagraphItem;
  dirtyText?: string;
  originalText?: string;
  isEntireNoteRunning = false;
  revisionView = false;
  diffMatchPatch = new DiffMatchPatch();
  isParagraphRunning = false;
  results: ParagraphResults | undefined = [];
  configs: ParagraphConfigResults | undefined = {};
  progress = 0;
  colWidthOption = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  editorSetting: ParagraphEditorSetting = {
    params: {},
    forms: {}
  };

  constructor(
    public messageService: Message,
    protected noteStatusService: NoteStatus,
    protected angularContextManager: AngularContextManager,
    protected cdr: ChangeDetectorRef
  ) {
    super(messageService);
  }

  abstract changeColWidth(needCommit: boolean, updateResult?: boolean): void;

  @MessageListener(OP.PROGRESS)
  onProgress(data: MessageReceiveDataTypeMap[OP.PROGRESS]) {
    if (data.id === this.paragraph?.id) {
      this.progress = data.progress;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.PARAGRAPH_STATUS)
  onParagraphStatus(data: MessageReceiveDataTypeMap[OP.PARAGRAPH_STATUS]) {
    if (data.id === this.paragraph?.id) {
      this.paragraph.status = data.status;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.NOTE_RUNNING_STATUS)
  noteRunningStatusChange(data: MessageReceiveDataTypeMap[OP.NOTE_RUNNING_STATUS]) {
    this.isEntireNoteRunning = data.status;
    this.cdr.markForCheck();
  }

  @MessageListener(OP.PARAS_INFO)
  updateParaInfos(data: MessageReceiveDataTypeMap[OP.PARAS_INFO]) {
    if (this.paragraph?.id === data.id) {
      this.paragraph.runtimeInfos = data.infos;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.EDITOR_SETTING)
  getEditorSetting(data: MessageReceiveDataTypeMap[OP.EDITOR_SETTING]) {
    if (this.paragraph?.id === data.paragraphId) {
      this.paragraph.config.editorSetting = {
        ...(this.paragraph.config.editorSetting ?? {
          params: {},
          forms: {}
        }),
        ...data.editor
      };
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.PARAGRAPH)
  paragraphData(data: MessageReceiveDataTypeMap[OP.PARAGRAPH]) {
    const oldPara = this.paragraph;
    if (!oldPara) {
      throw new Error('paragraph is not defined');
    }
    const newPara = data.paragraph;
    if (!newPara.results) {
      newPara.results = {};
    }
    if (this.isUpdateRequired(oldPara, newPara)) {
      this.updateParagraph(oldPara, newPara, () => {
        if (newPara.results && newPara.results.msg) {
          newPara.results.msg.forEach((newResult, idx) => {
            const oldResult =
              oldPara.results && oldPara.results.msg ? oldPara.results.msg[idx] : new ParagraphIResultsMsgItem();
            const newConfig = newPara.config.results ? newPara.config.results[idx] : { graph: new GraphConfig() };
            const oldConfig = oldPara.config.results ? oldPara.config.results[idx] : { graph: new GraphConfig() };
            if (!isEqual(newResult, oldResult) || !isEqual(newConfig, oldConfig)) {
              this.updateParagraphResult(idx, newConfig, newResult);
            }
          });
        }
        this.cdr.markForCheck();
      });
      this.cdr.markForCheck();
    }
  }

  abstract updateParagraphResult(
    resultIndex: number,
    config: ParagraphConfigResult,
    result: ParagraphIResultsMsgItem
  ): void;

  @MessageListener(OP.PATCH_PARAGRAPH)
  patchParagraph(data: MessageReceiveDataTypeMap[OP.PATCH_PARAGRAPH]) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    if (data.paragraphId === this.paragraph.id) {
      const patch = this.diffMatchPatch.patch_fromText(data.patch);
      if (!this.paragraph.text) {
        this.paragraph.text = '';
      }
      this.paragraph.text = this.diffMatchPatch.patch_apply(patch, this.paragraph.text)[0];
      this.originalText = this.paragraph.text;
      this.cdr.markForCheck();
    }
  }

  @MessageListener(OP.ANGULAR_OBJECT_UPDATE)
  angularObjectUpdate(data: AngularObjectUpdate) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    if (data.paragraphId === this.paragraph.id) {
      const { name, object } = data.angularObject;
      this.angularContextManager.setContextValue(name, object, data.paragraphId, false);
    }
  }

  @MessageListener(OP.ANGULAR_OBJECT_REMOVE)
  angularObjectRemove(data: AngularObjectRemove) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    if (data.paragraphId === this.paragraph.id) {
      this.angularContextManager.unsetContextValue(data.name, data.paragraphId, false);
    }
  }

  updateParagraph(oldPara: ParagraphItem, newPara: ParagraphItem, updateCallback: () => void) {
    // 1. can't update on revision view
    if (!this.revisionView) {
      // 2. get status, refreshed
      const statusChanged = newPara.status !== oldPara.status;
      const resultRefreshed =
        newPara.dateFinished !== oldPara.dateFinished ||
        isEmpty(newPara.results) !== isEmpty(oldPara.results) ||
        newPara.status === ParagraphStatus.ERROR ||
        (newPara.status === ParagraphStatus.FINISHED && statusChanged);

      // 3. update texts managed by paragraph
      this.updateAllScopeTexts(oldPara, newPara);
      // 4. execute callback to update result
      updateCallback();

      // 5. update remaining paragraph objects
      this.updateParagraphObjectWhenUpdated(newPara);

      // 6. handle scroll down by key properly if new paragraph is added
      if (statusChanged || resultRefreshed) {
        // when last paragraph runs, zeppelin automatically appends new paragraph.
        // this broadcast will focus to the newly inserted paragraph
        // TODO(hsuanxyz)
      }
      this.cdr.markForCheck();
    }
  }

  isUpdateRequired(oldPara: ParagraphItem, newPara: ParagraphItem): boolean {
    return (
      newPara.id === oldPara.id &&
      (newPara.dateCreated !== oldPara.dateCreated ||
        newPara.text !== oldPara.text ||
        newPara.dateFinished !== oldPara.dateFinished ||
        newPara.dateStarted !== oldPara.dateStarted ||
        newPara.dateUpdated !== oldPara.dateUpdated ||
        newPara.status !== oldPara.status ||
        newPara.jobName !== oldPara.jobName ||
        newPara.title !== oldPara.title ||
        isEmpty(newPara.results) !== isEmpty(oldPara.results) ||
        newPara.errorMessage !== oldPara.errorMessage ||
        !isEqual(newPara.settings, oldPara.settings) ||
        !isEqual(newPara.config, oldPara.config) ||
        !isEqual(newPara.runtimeInfos, oldPara.runtimeInfos))
    );
  }

  updateAllScopeTexts(oldPara: ParagraphItem, newPara: ParagraphItem) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    if (oldPara.text !== newPara.text) {
      if (this.dirtyText) {
        // check if editor has local update
        if (this.dirtyText === newPara.text) {
          // when local update is the same from remote, clear local update
          this.paragraph.text = newPara.text;
          this.dirtyText = undefined;
          this.originalText = newPara.text;
        } else {
          // if there're local update, keep it.
          this.paragraph.text = newPara.text;
        }
      } else {
        this.paragraph.text = newPara.text;
        this.originalText = newPara.text;
      }
    }
    this.cdr.markForCheck();
  }

  updateParagraphObjectWhenUpdated(newPara: ParagraphItem) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    if (this.paragraph.config.colWidth !== newPara.config.colWidth) {
      this.changeColWidth(false);
    }
    this.paragraph.aborted = newPara.aborted;
    this.paragraph.user = newPara.user;
    this.paragraph.dateUpdated = newPara.dateUpdated;
    this.paragraph.dateCreated = newPara.dateCreated;
    this.paragraph.dateFinished = newPara.dateFinished;
    this.paragraph.dateStarted = newPara.dateStarted;
    this.paragraph.errorMessage = newPara.errorMessage;
    this.paragraph.jobName = newPara.jobName;
    this.paragraph.title = newPara.title;
    this.paragraph.lineNumbers = newPara.lineNumbers;
    this.paragraph.status = newPara.status;
    this.paragraph.fontSize = newPara.fontSize;
    if (newPara.status !== ParagraphStatus.RUNNING) {
      this.paragraph.results = newPara.results;
    }
    this.paragraph.settings = newPara.settings;
    this.paragraph.runtimeInfos = newPara.runtimeInfos;
    this.isParagraphRunning = this.noteStatusService.isParagraphRunning(newPara);
    this.paragraph.config = newPara.config;
    this.initializeDefault(this.paragraph.config, this.paragraph.settings);
    this.setResults(this.paragraph);
    this.cdr.markForCheck();
  }

  setResults(paragraph: ParagraphItem) {
    if (paragraph.results) {
      this.results = paragraph.results.msg;
      this.configs = paragraph.config.results;
    }
    if (!paragraph.config) {
      paragraph.config = {};
    }
  }

  initializeDefault(config: ParagraphConfig, settings: ParagraphEditorSetting) {
    const forms = settings.forms;

    if (!config.colWidth) {
      config.colWidth = 12;
    }

    if (!config.fontSize) {
      config.fontSize = 9;
    }

    if (config.enabled === undefined) {
      config.enabled = true;
    }

    for (const idx in forms) {
      if (forms[idx]) {
        if (forms[idx].options) {
          if (config.runOnSelectionChange === undefined) {
            config.runOnSelectionChange = true;
          }
        }
      }
    }

    if (!config.results) {
      config.results = {};
    }

    if (!config.editorSetting) {
      config.editorSetting = {
        params: {},
        forms: {}
      };
    } else if (config.editorSetting.editOnDblClick) {
      this.editorSetting.isOutputHidden = config.editorSetting.editOnDblClick;
    }
  }

  runParagraphUsingSpell(paragraphText: string, magic: string, propagated: boolean) {
    // TODO(hsuanxyz)
  }

  runParagraphUsingBackendInterpreter(paragraphText: string) {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    this.messageService.runParagraph(
      this.paragraph.id,
      this.paragraph.title,
      paragraphText,
      this.paragraph.config,
      this.paragraph.settings.params
    );
  }

  cancelParagraph() {
    if (!this.paragraph) {
      throw new Error('paragraph is not defined');
    }
    this.messageService.cancelParagraph(this.paragraph.id);
  }
}
