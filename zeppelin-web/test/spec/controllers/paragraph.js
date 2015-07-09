'use strict';

describe('Controller: ParagraphCtrl', function () {

  // load the controller's module
  beforeEach(module('zeppelinWebApp'));

  var ParagraphCtrl, scope;

  // Initialize the controller and a mock scope
  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new();
    ParagraphCtrl = $controller('ParagraphCtrl', {
      $scope: scope
    });
  }));

  //Write test to test ParagraphCtrl
});
