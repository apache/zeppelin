import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class JobManagerService extends BaseRest {
  constructor(baseUrlService: BaseUrlService, private http: HttpClient) {
    super(baseUrlService);
  }

  startJob(noteId: string) {
    return this.http.post(this.restUrl`/notebook/job/${noteId}`, {});
  }

  stopJob(noteId: string) {
    return this.http.delete(this.restUrl`/notebook/job/${noteId}`, {});
  }
}
