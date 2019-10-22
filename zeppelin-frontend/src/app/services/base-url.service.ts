import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class BaseUrlService {
  getPort() {
    let port = Number(location.port);
    if (!port) {
      port = 80;
      if (location.protocol === 'https:') {
        port = 443;
      }
    }
    return port;
  }

  getWebsocketUrl() {
    const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${wsProtocol}//${location.hostname}:${this.getPort()}${this.skipTrailingSlash(location.pathname)}/ws`;
  }

  getBase() {
    return `${location.protocol}//${location.hostname}:${this.getPort()}${location.pathname}`;
  }

  getRestApiBase() {
    return this.skipTrailingSlash(this.getBase()) + '/api';
  }

  skipTrailingSlash(path) {
    return path.replace(/\/$/, '');
  }
}
