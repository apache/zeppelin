describe('Controller: ResultCtrl', function() {
  beforeEach(angular.mock.module('zeppelinWebApp'));

  let scope;
  let controller;
  let resultMock = {
  };
  let configMock = {
  };
  let paragraphMock = {
    id: 'p1',
    results: {
      msg: [],
    },
  };
  let route = {
    current: {
      pathParams: {
        noteId: 'noteId',
      },
    },
  };

  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();
    scope.$parent = $rootScope.$new(true, $rootScope);
    scope.$parent.paragraph = paragraphMock;

    controller = $controller('ResultCtrl', {
      $scope: scope,
      $route: route,
    });

    scope.init(resultMock, configMock, paragraphMock, 1);
  }));

  it('scope should be initialized', function() {
    expect(scope).toBeDefined();
    expect(controller).toBeDefined();
  });
});
