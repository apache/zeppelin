/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

describe('Factory: WebsocketEvent', function() {
  let openHandler;
  let closeHandler;
  let mockSocket;
  let rootScope;
  let websocketEvents;

  beforeEach(function() {
    mockSocket = {
      socket: {readyState: 1},
      reconnectIfNotNormalClose: false,
      onOpen: jasmine.createSpy('onOpen').and.callFake(function(handler) {
        openHandler = handler;
      }),
      onMessage: jasmine.createSpy('onMessage').and.returnValue(null),
      onError: jasmine.createSpy('onError').and.returnValue(null),
      onClose: jasmine.createSpy('onClose').and.callFake(function(handler) {
        closeHandler = handler;
      }),
      reconnect: jasmine.createSpy('reconnect'),
      send: jasmine.createSpy('send'),
    };

    angular.mock.module('zeppelinWebApp', function($provide) {
      $provide.value('$websocket', jasmine.createSpy('$websocket').and.returnValue(mockSocket));
    });

    angular.mock.inject(function(_websocketEvents_, _$rootScope_) {
      websocketEvents = _websocketEvents_;
      rootScope = _$rootScope_;
    });
  });

  it('should send only operation data and message metadata', function() {
    websocketEvents.sendNewEvent({op: 'PING'});

    const message = JSON.parse(mockSocket.send.calls.mostRecent().args[0]);
    expect(message.op).toBe('PING');
    expect(message.msgId).toBeDefined();
    expect(message.principal).toBeUndefined();
    expect(message.roles).toBeUndefined();
    expect(message.ticket).toBeUndefined();
  });

  it('should disable reconnect after a policy violation close', function() {
    spyOn(rootScope, '$broadcast');
    expect(mockSocket.reconnectIfNotNormalClose).toBe(true);

    closeHandler({code: 1008, reason: 'Session expired'});

    expect(mockSocket.reconnectIfNotNormalClose).toBe(false);
    expect(rootScope.$broadcast).toHaveBeenCalledWith('session_logout', {
      info: 'Session expired',
    });
  });

  it('should retain normal and retryable close behavior for other codes', function() {
    closeHandler({code: 1000});
    expect(mockSocket.reconnectIfNotNormalClose).toBe(true);

    closeHandler({code: 1006});
    expect(mockSocket.reconnectIfNotNormalClose).toBe(true);
  });

  it('should wait for a new authenticated connection before continuing after login', function() {
    const callback = jasmine.createSpy('authenticatedReconnect');
    mockSocket.reconnectIfNotNormalClose = false;

    websocketEvents.reconnect(callback);

    expect(mockSocket.reconnectIfNotNormalClose).toBe(true);
    expect(mockSocket.reconnect).toHaveBeenCalled();
    expect(callback).not.toHaveBeenCalled();

    openHandler();

    expect(callback).toHaveBeenCalled();
  });
});
