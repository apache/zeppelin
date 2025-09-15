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

import { Configuration } from '@zeppelin/sdk';
import { retry } from 'rxjs/operators';
import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class ConfigurationService extends BaseRest {
  private _configuration?: Configuration;

  constructor(private http: HttpClient, baseUrlService: BaseUrlService) {
    super(baseUrlService);
  }

  get configuration() {
    return this._configuration;
  }

  async initialize(): Promise<void> {
    this._configuration = await this.http
      .get<Configuration>(this.restUrl`/configurations/all`)
      .pipe(retry(3))
      .toPromise();
  }
}
