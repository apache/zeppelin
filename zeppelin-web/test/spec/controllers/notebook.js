'use strict';

describe('Controller: NotebookCtrl', function() {
  beforeEach(module('zeppelinWebApp'));

  var NotebookCtrl;
  var scope;
  var rootScope;
  var websocketMsgSrv;
  var routeParams;

  var baseUrlSrvMock = {
    getRestApiBase: function() {
      return 'http://localhost:8080';
    }
  };

  var noteMock = {
    id: 1,
    name: 'my notebook',
    config: {
      looknfeel: 'default'
    },
    paragraphs: [
      {id: 'p1', status: 'FINISHED'},
      {id: 'p2', status: 'FINISHED'},
      {id: 'p3', status: 'FINISHED'}
    ]
  };

  beforeEach(inject(function($controller, $rootScope, $injector, $routeParams, _websocketMsgSrv_) {
    rootScope = $rootScope;
    scope = $rootScope.$new();
    websocketMsgSrv = _websocketMsgSrv_;
    routeParams = $routeParams;
    NotebookCtrl = $controller('NotebookCtrl', {
      $scope: scope,
      baseUrlSrv: baseUrlSrvMock
    });

    scope.note = noteMock;
    spyOn($rootScope, '$broadcast').andCallThrough();
    websocketMsgSrv.deleteNotebook = jasmine.createSpy('websocketMsgSrv.deleteNotebook').andReturn(true);
    websocketMsgSrv.cloneNotebook = jasmine.createSpy('websocketMsgSrv.cloneNotebook').andReturn(true);
    websocketMsgSrv.updateNotebook = jasmine.createSpy('websocketMsgSrv.updateNotebook').andReturn(true);
  }));

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
    var nonExisting = scope.getCronOptionNameFromValue('0 * * * *');
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
    expect(nonExisting).toEqual('0 * * * *');
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

  it('should set connectedOnce flag to true on setConnectedStatus event', function() {
    rootScope.$broadcast('setConnectedStatus', false);
    expect(NotebookCtrl.connectedOnce).toEqual(true);
  });

  it('should call initNotebook when connectedOnce and param are true in setConnectedStatus event', function() {
    NotebookCtrl.connectedOnce = true;
    spyOn(NotebookCtrl, 'initNotebook');
    rootScope.$broadcast('setConnectedStatus', {});
    expect(NotebookCtrl.initNotebook).toHaveBeenCalled();
  });

  it('should removeNote call websocketMsgSrv.deleteNotebook', function() {
    spyOn(window, 'confirm').andReturn(true);
    scope.removeNote('NOTEID');
    expect(websocketMsgSrv.deleteNotebook).toHaveBeenCalled();
  });

  it('should cloneNote call websocketMsgSrv.cloneNotebook', function() {
    spyOn(window, 'confirm').andReturn(true);
    scope.cloneNote('NOTEID');
    expect(websocketMsgSrv.cloneNotebook).toHaveBeenCalled();
  });

  it('should toggleAllEditor toggle editorToggled and broadcast with openEditor or closeEditor', function() {
    scope.toggleAllEditor();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('closeEditor');
    scope.toggleAllEditor();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('openEditor');
  });

  it('should showAllEditor call broadcast with openEditor', function() {
    scope.showAllEditor();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('openEditor');
  });

  it('should hideAllEditor call broadcast with closeEditor', function() {
    scope.hideAllEditor();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('closeEditor');
  });

  it('should toggleAllTable toggle tableToggled and broadcast with openTable or closeTable', function() {
    scope.toggleAllTable();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('closeTable');
    scope.toggleAllTable();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('openTable');
  });

  it('should showAllTable call broadcast with openTable', function() {
    scope.showAllTable();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('openTable');
  });

  it('should hideAllTable call broadcast with closeTable', function() {
    scope.hideAllTable();
    expect(rootScope.$broadcast).toHaveBeenCalledWith('closeTable');
  });

  it('should isNoteRunning return appropriate status', function() {
    expect(scope.isNoteRunning()).toEqual(false);
    scope.note.paragraphs[2].status = 'PENDING';
    expect(scope.isNoteRunning()).toEqual(true);
    scope.note = null;
    expect(scope.isNoteRunning()).toEqual(false);
  });

  it('should set the right value for looknfeel when setLookAndFeel is called', function() {
    spyOn(scope, 'setLookAndFeel');
    scope.setLookAndFeel('simple');
    expect(scope.setLookAndFeel).toHaveBeenCalled();
  });

  it('should sendNewName call websocketMsgSrv.updateNotebook', function() {
    scope.sendNewName();
    expect(scope.showEditor).toEqual(false);
    scope.note.name = 'new name';
    expect(websocketMsgSrv.updateNotebook).toHaveBeenCalled();
  });

});
