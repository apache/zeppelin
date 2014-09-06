'use strict';

describe('Controller: NotebookCtrl', function () {

  // load the controller's module
  beforeEach(module('zeppelinWeb2App'));

  var NotebookCtrl,
    scope;

  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new();
    NotebookCtrl = $controller('NotebookCtrl', {
      $scope: scope
    });
  }));

  it('should attach a list of awesomeThings to the scope', function () {
    expect(scope.awesomeThings.length).toBe(3);
  });
});
