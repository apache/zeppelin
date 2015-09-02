'use strict';

describe('Directive: ngEnter', function () {

  // load the directive's module
  beforeEach(module('zeppelinWebApp'));

  var element,
    scope;

  beforeEach(inject(function ($rootScope) {
    scope = $rootScope.$new();
  }));

  it('should be define', inject(function ($compile) {
    element = angular.element('<ng-enter></ng-enter>');
    element = $compile(element)(scope);
    expect(element.text()).toBeDefined();
  }));

  //Test the rest of function in ngEnter
/*  it('should make hidden element visible', inject(function ($compile) {
    element = angular.element('<ng-enter></ng-enter>');
    element = $compile(element)(scope);
    expect(element.text()).toBe('this is the ngEnter directive');
  }));*/
});

