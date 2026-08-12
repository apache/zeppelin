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

describe('Controller: Interpreter', function() {
  beforeEach(angular.mock.module('zeppelinWebApp'));

  const baseUrlSrvMock = {
    getBase: () => '/',
    getRestApiBase: () => '',
  };

  let $controller;
  let $httpBackend;
  let $rootScope;
  let ngToast;

  beforeEach(inject((_$controller_, _$httpBackend_, _$rootScope_, _ngToast_) => {
    $controller = _$controller_;
    $httpBackend = _$httpBackend_;
    $rootScope = _$rootScope_;
    ngToast = _ngToast_;
  }));

  afterEach(function() {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  [401, 403].forEach((status) => {
    it(`should display an authorization error for HTTP ${status}`, function() {
      spyOn(ngToast, 'danger');
      spyOn(window, 'setTimeout');

      $httpBackend.expectGET('/interpreter/property/types').respond(200, {body: []});
      $httpBackend.expectGET('/interpreter/setting').respond(status, {});
      $httpBackend.expectGET('/interpreter').respond(200, {body: []});
      $httpBackend.expectGET('/interpreter/repository').respond(200, {body: []});

      $controller('InterpreterCtrl', {
        $scope: $rootScope.$new(),
        baseUrlSrv: baseUrlSrvMock,
      });
      $httpBackend.flush();

      expect(ngToast.danger).toHaveBeenCalledWith({
        content: 'You don\'t have permission on this page',
        verticalPosition: 'bottom',
        timeout: '3000',
      });
      expect(window.setTimeout).toHaveBeenCalledWith(jasmine.any(Function), 3000);
    });
  });
});
