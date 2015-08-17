'use strict';

describe('Controller: MainCtrl', function () {

  // load the controller's module
  beforeEach(module('zeppelinWebApp'));

  var MainCtrl,
    scope,
    rootScope;

  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    rootScope = $rootScope;
    scope = $rootScope.$new();
    MainCtrl = $controller('MainCtrl', {
      $scope: scope
    });
  }));

  it('should attach a asIframe to the scope and the default value should be false', function () {
    expect(scope.asIframe).toBeDefined();
    expect(scope.asIframe).toEqual(false);
  });

  it('should set the default value of "looknfeel to "default"', function () {
    expect(scope.looknfeel).toEqual('default');
  });

  it('should set asIframe flag to true when a controller broadcasts setIframe event', function () {
    rootScope.$broadcast('setIframe', true);
    expect(scope.asIframe).toEqual(true);
  });

});
