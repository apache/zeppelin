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

import { NotebookSearchResultItem } from '@zeppelin/interfaces';
import { BaseRest } from '@zeppelin/services/base-rest';
import { BaseUrlService } from '@zeppelin/services/base-url.service';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class NotebookSearchService extends BaseRest {
  private queryStr$ = new BehaviorSubject<string | null>(null);

  constructor(baseUrlService: BaseUrlService, private http: HttpClient) {
    super(baseUrlService);
  }

  queried() {
    return this.queryStr$.asObservable();
  }

  clear() {
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
}
