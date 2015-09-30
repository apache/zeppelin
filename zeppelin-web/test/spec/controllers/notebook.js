'use strict';

describe('Controller: NotebookCtrl', function() {
  beforeEach(module('zeppelinWebApp'));

  var NotebookCtrl;
  var scope;

  var websocketMsgSrvMock = {
    getNotebook: function() {}
  };

  var baseUrlSrvMock = {
    getRestApiBase: function() {
      return 'http://localhost:8080';
    }
  };

  var noteMock = {
    id: 1,
    name: 'my notebook',
    config: {},
  };

  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();
    NotebookCtrl = $controller('NotebookCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      baseUrlSrv: baseUrlSrvMock
    });
  }));

  beforeEach(function() {
    scope.note = noteMock;
  });

  var functions = ['getCronOptionNameFromValue', 'removeNote', 'runNote', 'saveNote', 'toggleAllEditor',
    'showAllEditor', 'hideAllEditor', 'toggleAllTable', 'hideAllTable', 'showAllTable', 'isNoteRunning',
    'killSaveTimer', 'startSaveTimer', 'setLookAndFeel', 'setCronScheduler', 'setConfig', 'sendNewName',
    'openSetting', 'closeSetting', 'saveSetting', 'toggleSetting'];

  functions.forEach(function(fn) {
    it('check for scope functions to be defined : ' + fn, function() {
      expect(scope[fn]).toBeDefined();
    });
  });

  it('should set default value of "showEditor" to false', function() {
    expect(scope.showEditor).toEqual(false);
  });

  it('should set default value of "editorToggled" to false', function() {
    expect(scope.editorToggled).toEqual(false);
  });

  it('should set "showSetting" to true when openSetting() is called', function() {
    scope.openSetting();
    expect(scope.showSetting).toEqual(true);
  });

  it('should set "showSetting" to false when closeSetting() is called', function() {
    scope.closeSetting();
    expect(scope.showSetting).toEqual(false);
  });

  it('should return the correct value for getCronOptionNameFromValue()', function() {
    var none = scope.getCronOptionNameFromValue();
    var oneMin = scope.getCronOptionNameFromValue('0 0/1 * * * ?');
    var fiveMin = scope.getCronOptionNameFromValue('0 0/5 * * * ?');
    var oneHour = scope.getCronOptionNameFromValue('0 0 0/1 * * ?');
    var threeHours = scope.getCronOptionNameFromValue('0 0 0/3 * * ?');
    var sixHours = scope.getCronOptionNameFromValue('0 0 0/6 * * ?');
    var twelveHours =  scope.getCronOptionNameFromValue('0 0 0/12 * * ?');
    var oneDay = scope.getCronOptionNameFromValue('0 0 0 * * ?');

    expect(none).toEqual('');
    expect(oneMin).toEqual('1m');
    expect(fiveMin).toEqual('5m');
    expect(oneHour).toEqual('1h');
    expect(threeHours).toEqual('3h');
    expect(sixHours).toEqual('6h');
    expect(twelveHours).toEqual('12h');
    expect(oneDay).toEqual('1d');
  });

  it('should have "isNoteDirty" as null by default', function() {
    expect(scope.isNoteDirty).toEqual(null);
  });

  it('should first call killSaveTimer() when calling startSaveTimer()', function() {
    expect(scope.saveTimer).toEqual(null);
    spyOn(scope, 'killSaveTimer');
    scope.startSaveTimer();
    expect(scope.killSaveTimer).toHaveBeenCalled();
  });

  it('should set "saveTimer" when saveTimer() and killSaveTimer() are called', function() {
    expect(scope.saveTimer).toEqual(null);
    scope.startSaveTimer();
    expect(scope.saveTimer).toBeTruthy();
    scope.killSaveTimer();
    expect(scope.saveTimer).toEqual(null);
  });

});