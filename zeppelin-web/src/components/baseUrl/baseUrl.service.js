/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

angular.module('zeppelinWebApp').service('baseUrlSrv', function() {

  this.getPort = function() {
    var port = Number(location.port);
    if (location.protocol !== 'https:' && (port === 'undifined' || port === 0)) {
      port = 80;
    } else if (location.protocol === 'https:' && (port === 'undifined' || port === 0)) {
      port = 443;
    } else if (port === 3333 || port === 9000) {
      port = 8080;
    }
    return port+1;
  };

  this.getWebsocketProtocol = function() {
    var protocol = 'ws';
    if (location.protocol === 'https:') {
      protocol = 'wss';
    }
    return protocol;
  };

  this.getRestApiBase = function() {
    var port = Number(location.port);
    if (port === 'undefined' || port === 0) {
      port = 80;
      if (location.protocol === 'https:') {
        port = 443;
      }
    }

    if (port === 3333 || port === 9000) {
      port = 8080;
    }
    return location.protocol + '//' + location.hostname + ':' + port + '/api';
  };

});