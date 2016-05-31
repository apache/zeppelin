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

angular.module('zeppelinWebApp').factory('websocketEvents', function($rootScope, $websocket, $location, baseUrlSrv) {
  var websocketCalls = { notebookServer : {}, jobManagerServer : {} };

  // create socket for notebook server
  websocketCalls.notebookServer.ws = $websocket(baseUrlSrv.getWebsocketUrl());
  websocketCalls.notebookServer.ws.reconnectIfNotNormalClose = true;

  websocketCalls.notebookServer.ws.onOpen(function() {
    console.log('Websocket Notebook Server created');
    $rootScope.$broadcast('setNotebookWSConnectedStatus', true);
    setInterval(function(){
      websocketCalls.notebookServer.sendNewEvent({op: 'PING'});
    }, 10000);
  });

  websocketCalls.notebookServer.sendNewEvent = function(data) {
    if ($rootScope.ticket !== undefined) {
      data.principal = $rootScope.ticket.principal;
      data.ticket = $rootScope.ticket.ticket;
      data.roles = $rootScope.ticket.roles;
    } else {
      data.principal = '';
      data.ticket = '';
      data.roles = '';
    }
    console.log('Send Notebook Server >> %o, %o, %o, %o, %o', data.op, data.principal, data.ticket, data.roles, data);
    websocketCalls.notebookServer.ws.send(JSON.stringify(data));
  };

  websocketCalls.notebookServer.isConnected = function() {
    return (websocketCalls.notebookServer.ws.socket.readyState === 1);
  };

  websocketCalls.notebookServer.ws.onMessage(function(event) {
    var payload;
    if (event.data) {
      payload = angular.fromJson(event.data);
    }
    console.log('Receive Notebook Server << %o, %o', payload.op, payload);
    var op = payload.op;
    var data = payload.data;
    if (op === 'NOTE') {
      $rootScope.$broadcast('setNoteContent', data.note);
    } else if (op === 'NEW_NOTE') {
      $location.path('notebook/' + data.note.id);
    } else if (op === 'NOTES_INFO') {
      $rootScope.$broadcast('setNoteMenu', data.notes);
    } else if (op === 'AUTH_INFO') {
      BootstrapDialog.show({
          closable: true,
          title: 'Insufficient privileges',
          message: data.info.toString(),
          buttons: [{
              label: 'Login',
              action: function(dialog) {
                  dialog.close();
                  angular.element('#loginModal').modal({
                    show: 'true'
                  });
              }
          }, {
              label: 'Cancel',
              action: function(dialog){
                 dialog.close();
              }
          }]
      });
    } else if (op === 'PARAGRAPH') {
      $rootScope.$broadcast('updateParagraph', data);
    } else if (op === 'PARAGRAPH_APPEND_OUTPUT') {
      $rootScope.$broadcast('appendParagraphOutput', data);
    } else if (op === 'PARAGRAPH_UPDATE_OUTPUT') {
      $rootScope.$broadcast('updateParagraphOutput', data);
    } else if (op === 'PROGRESS') {
      $rootScope.$broadcast('updateProgress', data);
    } else if (op === 'COMPLETION_LIST') {
      $rootScope.$broadcast('completionList', data);
    } else if (op === 'ANGULAR_OBJECT_UPDATE') {
      $rootScope.$broadcast('angularObjectUpdate', data);
    } else if (op === 'ANGULAR_OBJECT_REMOVE') {
      $rootScope.$broadcast('angularObjectRemove', data);
    }
  });

  websocketCalls.notebookServer.ws.onError(function(event) {
    console.log('[notebookWS] Notebook Server message: ', event);
    $rootScope.$broadcast('setNotebookWSConnectedStatus', false);
  });

  websocketCalls.notebookServer.ws.onClose(function(event) {
    console.log('[notebookWS] close message: ', event);
    $rootScope.$broadcast('setNotebookWSConnectedStatus', false);
  });

  // create socket for job manager server
  websocketCalls.jobManagerServer.ws = $websocket(baseUrlSrv.getJobManagerWebsocketUrl());
  websocketCalls.jobManagerServer.ws.reconnectIfNotNormalClose = true;

  websocketCalls.jobManagerServer.ws.onOpen(function() {
    console.log('Websocket Job Manager Server created');
    $rootScope.$broadcast('setJobManagerWSConnectedStatus', true);
    setInterval(function(){
      websocketCalls.jobManagerServer.sendNewEvent({op: 'PING'});
    }, 10000);
  });

  websocketCalls.jobManagerServer.sendNewEvent = function(data) {
    if ($rootScope.ticket !== undefined) {
      data.principal = $rootScope.ticket.principal;
      data.ticket = $rootScope.ticket.ticket;
      data.roles = $rootScope.ticket.roles;
    } else {
      data.principal = '';
      data.ticket = '';
      data.roles = '';
    }
    console.log('Send Job Manager Server >> %o, %o, %o, %o, %o', data.op, data.principal, data.ticket, data.roles, data);
    websocketCalls.jobManagerServer.ws.send(JSON.stringify(data));
  };

  websocketCalls.jobManagerServer.isConnected = function() {
    return (websocketCalls.notebookServer.ws.socket.readyState === 1);
  };

  websocketCalls.jobManagerServer.ws.onMessage(function(event) {
    var payload;
    if (event.data) {
      payload = angular.fromJson(event.data);
    }
    console.log('Receive Job Manager Server<< %o, %o', payload.op, payload);
    var op = payload.op;
    var data = payload.data;
    if (op === 'LIST_NOTEBOOK_JOBS') {
      $rootScope.$broadcast('setNotebookJobs', data.notebookJobs);
    } else if (op === 'LIST_UPDATE_NOTEBOOK_JOBS') {
      $rootScope.$broadcast('setUpdateNotebookJobs', data.notebookRunningJobs);
    }
  });

  websocketCalls.jobManagerServer.ws.onError(function(event) {
    console.log('[jobManagerWS] error message: ', event);
    $rootScope.$broadcast('setJobManagerWSConnectedStatus', false);
  });

  websocketCalls.jobManagerServer.ws.onClose(function(event) {
    console.log('[jobManagerWS] close message: ', event);
    $rootScope.$broadcast('setJobManagerWSConnectedStatus', false);
  });

  return websocketCalls;
});
