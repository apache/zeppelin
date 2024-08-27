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

import { NotebookRepo, NotebookRepoPutData } from '@zeppelin/interfaces';

import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class NotebookRepoService extends BaseRest {
  constructor(baseUrlService: BaseUrlService, private http: HttpClient) {
    super(baseUrlService);
  }

  getRepos() {
    return this.http.get<NotebookRepo[]>(this.restUrl`/notebook-repositories`);
  }

  updateRepo(repo: NotebookRepoPutData) {
    return this.http.put(this.restUrl`/notebook-repositories`, repo);
  }
}
