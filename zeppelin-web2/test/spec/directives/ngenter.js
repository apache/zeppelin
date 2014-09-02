'use strict';

describe('Directive: ngEnter', function () {

  // load the directive's module
  beforeEach(module('zeppelinWeb2App'));

  var element,
    scope;

  beforeEach(inject(function ($rootScope) {
    scope = $rootScope.$new();
  }));

  it('should make hidden element visible', inject(function ($compile) {
    element = angular.element('<ng-enter></ng-enter>');
    element = $compile(element)(scope);
    expect(element.text()).toBe('this is the ngEnter directive');
  }));
});
