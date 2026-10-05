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

describe('Controller: LoginCtrl session logout', function() {
  beforeEach(angular.mock.module('zeppelinWebApp'));

  let rootScope;
  let scope;
  let fixture;
  let modal;
  let timeout;

  beforeEach(inject(function($controller, $rootScope, $compile, $timeout) {
    timeout = $timeout;
    rootScope = $rootScope;
    scope = rootScope.$new();
    rootScope.userName = 'admin';
    scope.navbar = {showLoginWindow: angular.noop};
    fixture = angular.element('<div><button class="nav-login-btn" data-toggle="modal" ' +
      'data-target="#loginModal" ng-click="navbar.showLoginWindow()">Login</button>' +
      '<div id="loginModal" class="modal" ' +
      'tabindex="-1"><div class="modal-dialog"><div class="modal-content">' +
      '<span class="error">{{loginParams.errorText}}</span>' +
      '<input id="userName" ng-model="loginParams.userName"></div></div></div></div>');
    $compile(fixture)(scope);
    angular.element(document.body).append(fixture);
    modal = fixture.find('#loginModal');
    $controller('LoginCtrl', {
      $scope: scope,
      baseUrlSrv: {},
      $location: {path: function() {
        return {search: angular.noop};
      }},
    });
    scope.$digest();
  }));

  afterEach(function() {
    modal.modal('hide');
    fixture.remove();
    scope.$destroy();
  });

  it('opens a closed login dialog after session logout', function() {
    rootScope.$broadcast('session_logout', {info: 'Session expired'});
    timeout.flush(1000);
    expect(modal.hasClass('in')).toBe(true);
    expect(modal.find('.error').text()).toBe('Session expired');
    timeout.flush(500);
    expect(document.activeElement).toBe(modal.find('#userName')[0]);
  });

  it('keeps a dialog opened by the user visible when delayed session logout runs', function() {
    rootScope.$broadcast('session_logout', {info: 'Session expired'});
    fixture.find('.nav-login-btn').click();
    expect(modal.hasClass('in')).toBe(true);
    timeout.flush(1000);
    expect(modal.hasClass('in')).toBe(true);
    timeout.flush(500);
  });
});
