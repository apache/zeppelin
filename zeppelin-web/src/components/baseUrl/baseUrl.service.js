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

  /** Get the current port of the websocket
  *
  * When running Zeppelin, the body of this function will be dynamically
  * overridden with the AppScriptServlet from zeppelin-site.xml config value.
  *
  * If the config value is not defined, it defaults to the HTTP port + 1
  *
  * In the case of running "grunt serve", this function will appear
  * as is.
  */
  
  /* @preserve AppScriptServlet - getPort */
  this.getPort = function() {
    var port = Number(location.port);
    if (location.protocol !== 'https:' && !port) {
      port = 80;
    } else if (location.protocol === 'https:' && !port) {
      port = 443;
    } else if (port === 3333 || port === 9000) {
      port = 8080;
    }
    return port + 1;
  };
  /* @preserve AppScriptServlet - close */

  this.getWebsocketProtocol = function() {
    return location.protocol === 'https:' ? 'wss' : 'ws';
  };

  this.getRestApiBase = function() {
    var port = Number(location.port);
    if (!port) {
      port = 80;
      if (location.protocol === 'https:') {
        port = 443;
      }
    }

    if (port === 3333 || port === 9000) {
      port = 8080;
    }
    return location.protocol + '//' + location.hostname + ':' + port + skipTrailingSlash(location.pathname) + '/api';
  };
  
  var skipTrailingSlash = function(path) {
    return path.replace(/\/$/, '');
  };

});