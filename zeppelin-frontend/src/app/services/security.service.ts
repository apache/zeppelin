import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { Permissions, SecurityUserList } from '@zeppelin/interfaces';

import { BaseRest } from './base-rest';
import { BaseUrlService } from './base-url.service';

@Injectable({
  providedIn: 'root'
})
export class SecurityService extends BaseRest {
  constructor(baseUrlService: BaseUrlService, private http: HttpClient) {
    super(baseUrlService);
  }

  searchUsers(term: string) {
    return this.http.get<SecurityUserList>(this.restUrl`/security/userlist/${term}`);
  }

  getPermissions(id: string) {
    return this.http.get<Permissions>(this.restUrl`/notebook/${id}/permissions`);
  }

  setPermissions(id: string, permissions: Permissions) {
    return this.http.put<Permissions>(this.restUrl`/notebook/${id}/permissions`, permissions);
  }
}
