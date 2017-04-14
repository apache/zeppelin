describe('Controller: NavCtrl', function () {
  // load the controller's module
  beforeEach(angular.mock.module('zeppelinWebApp'))
  let NavCtrl
  let scope
  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new()
    NavCtrl = $controller('NavCtrl', {
      $scope: scope
    })

    it('NavCtrl to toBeDefined', function () {
      expect(NavCtrl).toBeDefined()
      expect(NavCtrl.loadNotes).toBeDefined()
    })
  }))
})
