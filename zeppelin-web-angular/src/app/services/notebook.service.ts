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

import { NotebookCapabilities, NotebookSearchResultItem } from '@zeppelin/interfaces';
import { BehaviorSubject } from 'rxjs';
import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class NotebookService extends BaseRest {
  private queryStr$ = new BehaviorSubject<string | null>(null);

  constructor(private http: HttpClient, baseUrlService: BaseUrlService) {
    super(baseUrlService);
  }

  queried() {
    return this.queryStr$.asObservable();
  }

  clearQuery() {
    this.queryStr$.next(null);
  }

  search(query: string) {
    this.queryStr$.next(query);
    return this.http.get<NotebookSearchResultItem[]>(this.restUrl`/notebook/search`, {
      params: {
        q: query
      }
    });
  }

  capabilities() {
    return this.http.get<NotebookCapabilities>(this.restUrl`/notebook/capabilities`);
  }
}
