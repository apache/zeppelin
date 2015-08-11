'use strict';

describe('Controller: NotebookCtrl', function() {

  // load the controller's module
  beforeEach(module('zeppelinWebApp'));

  var NotebookCtrl, scope;

  // Initialize the controller and a mock scope
  beforeEach(inject(function($controller, $rootScope/*, websocketMsgSrv, baseUrlSrv*/) {
    scope = $rootScope.$new();
    NotebookCtrl = $controller('NotebookCtrl', {
      $scope: scope
    });
  }));

  //Test Can be writting for to test NotebookCtrl

});
