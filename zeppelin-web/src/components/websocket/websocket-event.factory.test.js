/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

describe('Factory: websocketEvents', function() {
  let fakeWebsocket;
  let messageCallback;
  let ngToast;
  let rootScope;
  let websocketEvents;

  beforeEach(function() {
    fakeWebsocket = {
      onOpen: jasmine.createSpy('onOpen'),
      onMessage: function(callback) {
        messageCallback = callback;
      },
      onError: jasmine.createSpy('onError'),
      onClose: jasmine.createSpy('onClose'),
      send: jasmine.createSpy('send'),
      socket: {readyState: 1},
    };
    ngToast = {info: jasmine.createSpy('info')};

    angular.mock.module('zeppelinWebApp', function($provide) {
      $provide.value('$websocket', function() {
        return fakeWebsocket;
      });
      $provide.value('baseUrlSrv', {getWebsocketUrl: function() {
        return 'ws://localhost/ws';
      }});
      $provide.value('saveAsService', {saveAs: angular.noop});
      $provide.value('ngToast', ngToast);
    });
  });

  beforeEach(inject(function($rootScope, _websocketEvents_) {
    rootScope = $rootScope;
    websocketEvents = _websocketEvents_;
  }));

  it('does not log the ticket or payload when sending a message', function() {
    const ticket = 'websocket-ticket-secret';
    const payloadSecret = 'paragraph-payload-secret';
    rootScope.ticket = {
      principal: 'test-user',
      ticket: ticket,
      roles: '["users"]',
    };
    spyOn(console, 'log');

    websocketEvents.sendNewEvent({
      op: 'RUN_PARAGRAPH',
      data: {paragraph: payloadSecret},
    });

    const sentMessage = JSON.parse(fakeWebsocket.send.calls.mostRecent().args[0]);
    expect(sentMessage.ticket).toBe(ticket);
    expect(sentMessage.data.paragraph).toBe(payloadSecret);
    expect(console.log).toHaveBeenCalledWith('Send >> %o, %o', 'RUN_PARAGRAPH', 'test-user');
    const consoleOutput = JSON.stringify(console.log.calls.allArgs());
    expect(consoleOutput).not.toContain(ticket);
    expect(consoleOutput).not.toContain(payloadSecret);
  });

  it('does not log the payload when receiving a message', function() {
    const payloadSecret = 'notice-payload-secret';
    spyOn(console, 'log');

    messageCallback({
      data: JSON.stringify({
        op: 'NOTICE',
        data: {notice: payloadSecret},
      }),
    });

    expect(ngToast.info).toHaveBeenCalledWith(payloadSecret);
    expect(console.log).toHaveBeenCalledWith('Receive << %o', 'NOTICE');
    expect(JSON.stringify(console.log.calls.allArgs())).not.toContain(payloadSecret);
  });
});
