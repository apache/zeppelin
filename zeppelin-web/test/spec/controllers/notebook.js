'use strict';

describe('Controller: NotebookCtrl', function() {

  // load the controller's module
  beforeEach(module('zeppelinWebApp'));

  var NotebookCtrl, scope;

  var websocketMsgSrvMock = {
    getNotebook:function() {}
  };

  var baseUrlSrvMock = {
    getRestApiBase: function () {
      return 'http://localhost:8080';
    }
  };

  var noteMock = {
    id: 1,
    config: {},
  };

  // Initialize the controller and a mock scope
  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();
    NotebookCtrl = $controller('NotebookCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      baseUrlSrv: baseUrlSrvMock
    });
  }));

  beforeEach(function () {
    scope.note = noteMock;
  });

  it('should set default value of "showEditor" to false', function () {
    expect(scope.showEditor).toEqual(false);
  });

  it('should set default value of "editorToggled" to false', function () {
    expect(scope.editorToggled).toEqual(false);
  });

  it('should set showSetting to true when openSetting is called', function () {
    scope.openSetting();
    expect(scope.showSetting).toEqual(true);
  });

  it('should set showSetting to false when closeSetting is called', function () {
    scope.closeSetting();
    expect(scope.showSetting).toEqual(false);
  });

  it('should return the correct value for getCronOptionNameFromValue', function () {
    var oneMin = scope.getCronOptionNameFromValue('0 0/1 * * * ?');
    var oneHour = scope.getCronOptionNameFromValue('0 0 0/1 * * ?');
    expect(oneMin).toEqual('1m');
    expect(oneHour).toEqual('1h');
  });

  it('default value for isNoteDirty should be null', function () {
    expect(scope.isNoteDirty).toEqual(null);
  });

  it('calling startSaveTimer should first call killSaveTimer and start a new timer', function () {
    expect(scope.saveTimer).toEqual(null);
    scope.startSaveTimer();
    expect(scope.isNoteDirty).toEqual(true);
    expect(scope.saveTimer).toBeTruthy();
  });

  it('calling killSaveTimer should clear saveTimer flag', function () {
    scope.killSaveTimer();
    expect(scope.saveTimer).toEqual(null);
  });


});
