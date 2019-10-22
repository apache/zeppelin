import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class NoteVarShareService {
  private store = new Map();

  clear() {
    this.store.clear();
  }

  set(key, value) {
    this.store.set(key, value);
  }

  get(key) {
    return this.store.get(key);
  }

  del(key) {
    return this.store.delete(key);
  }

  constructor() {}
}
