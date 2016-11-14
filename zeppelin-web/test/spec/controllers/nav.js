'use strict';
describe('Controller: NavCtrl', function() {
  // load the controller's module
  beforeEach(module('zeppelinWebApp'));
  var NavCtrl;
  var scope;
  // Initialize the controller and a mock scope
  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();
    NavCtrl = $controller('NavCtrl', {
      $scope: scope
    });

    it('NavCtrl to toBeDefined', function() {
      expect(NavCtrl).toBeDefined();
      expect(NavCtrl.loadNotes).toBeDefined();
    });
  }));
});
