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

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import {
  CreateInterpreterRepositoryForm,
  Interpreter,
  InterpreterMap,
  InterpreterPropertyTypes,
  InterpreterRepository
} from '@zeppelin/interfaces';
import { InterpreterItem } from '@zeppelin/sdk';

import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class InterpreterService extends BaseRest {
  constructor(baseUrlService: BaseUrlService, private http: HttpClient) {
    super(baseUrlService);
  }

  getRepositories() {
    return this.http.get<InterpreterRepository[]>(this.restUrl`/interpreter/repository`);
  }

  addRepository(repo: CreateInterpreterRepositoryForm) {
    return this.http.post(this.restUrl`/interpreter/repository`, repo);
  }

  removeRepository(repoId: string) {
    return this.http.delete(this.restUrl`/interpreter/repository/${repoId}`);
  }

  getInterpretersSetting() {
    return this.http.get<Interpreter[]>(this.restUrl`/interpreter/setting`);
  }

  getAvailableInterpreters() {
    return this.http.get<InterpreterMap>(this.restUrl`/interpreter`);
  }

  getAvailableInterpreterPropertyTypes() {
    return this.http.get<InterpreterPropertyTypes[]>(this.restUrl`/interpreter/property/types`);
  }

  addInterpreterSetting(interpreter: Interpreter) {
    return this.http.post<Interpreter>(this.restUrl`/interpreter/setting`, interpreter);
  }

  updateInterpreter(interpreter: Interpreter) {
    const { option, properties, dependencies } = interpreter;
    return this.http.put<Interpreter>(this.restUrl`/interpreter/setting/${interpreter.name}`, {
      option,
      properties,
      dependencies
    });
  }

  restartInterpreter(interpreterId: string, noteId: string) {
    return this.http.put<InterpreterItem>(this.restUrl`/interpreter/setting/restart/${interpreterId}`, { noteId });
  }

  removeInterpreterSetting(settingId: string) {
    return this.http.delete(this.restUrl`/interpreter/setting/${settingId}`);
  }

  restartInterpreterSetting(settingId: string) {
    return this.http.put(this.restUrl`/interpreter/setting/restart/${settingId}`, null);
  }
}
