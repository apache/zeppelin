describe('Controller: NotebookCtrl', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let scope

  let websocketMsgSrvMock = {
    getNote: function () {},
    listRevisionHistory: function () {},
    getInterpreterBindings: function () {},
    updateNote: function () {},
    renameNote: function () {}
  }

  let baseUrlSrvMock = {
    getRestApiBase: function () {
      return 'http://localhost:8080'
    }
  }

  let noteMock = {
    id: 1,
    name: 'my note',
    config: {},
  }

  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new()
    $controller('NotebookCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      baseUrlSrv: baseUrlSrvMock
    })
  }))

  beforeEach(function () {
    scope.note = noteMock
  })

  let functions = ['getCronOptionNameFromValue', 'removeNote', 'runAllParagraphs', 'saveNote', 'toggleAllEditor',
    'showAllEditor', 'hideAllEditor', 'toggleAllTable', 'hideAllTable', 'showAllTable', 'isNoteRunning',
    'killSaveTimer', 'startSaveTimer', 'setLookAndFeel', 'setCronScheduler', 'setConfig', 'updateNoteName',
    'openSetting', 'closeSetting', 'saveSetting', 'toggleSetting']

  functions.forEach(function (fn) {
    it('check for scope functions to be defined : ' + fn, function () {
      expect(scope[fn]).toBeDefined()
    })
  })

  it('should set default value of "editorToggled" to false', function () {
    expect(scope.editorToggled).toEqual(false)
  })

  it('should set "showSetting" to true when openSetting() is called', function () {
    scope.openSetting()
    expect(scope.showSetting).toEqual(true)
  })

  it('should set "showSetting" to false when closeSetting() is called', function () {
    scope.closeSetting()
    expect(scope.showSetting).toEqual(false)
  })

  it('should return the correct value for getCronOptionNameFromValue()', function () {
    let none = scope.getCronOptionNameFromValue()
    let oneMin = scope.getCronOptionNameFromValue('0 0/1 * * * ?')
    let fiveMin = scope.getCronOptionNameFromValue('0 0/5 * * * ?')
    let oneHour = scope.getCronOptionNameFromValue('0 0 0/1 * * ?')
    let threeHours = scope.getCronOptionNameFromValue('0 0 0/3 * * ?')
    let sixHours = scope.getCronOptionNameFromValue('0 0 0/6 * * ?')
    let twelveHours = scope.getCronOptionNameFromValue('0 0 0/12 * * ?')
    let oneDay = scope.getCronOptionNameFromValue('0 0 0 * * ?')

    expect(none).toEqual('')
    expect(oneMin).toEqual('1m')
    expect(fiveMin).toEqual('5m')
    expect(oneHour).toEqual('1h')
    expect(threeHours).toEqual('3h')
    expect(sixHours).toEqual('6h')
    expect(twelveHours).toEqual('12h')
    expect(oneDay).toEqual('1d')
  })

  it('should have "isNoteDirty" as null by default', function () {
    expect(scope.isNoteDirty).toEqual(null)
  })

  it('should first call killSaveTimer() when calling startSaveTimer()', function () {
    expect(scope.saveTimer).toEqual(null)
    spyOn(scope, 'killSaveTimer')
    scope.startSaveTimer()
    expect(scope.killSaveTimer).toHaveBeenCalled()
  })

  it('should set "saveTimer" when saveTimer() and killSaveTimer() are called', function () {
    expect(scope.saveTimer).toEqual(null)
    scope.startSaveTimer()
    expect(scope.saveTimer).toBeTruthy()
    scope.killSaveTimer()
    expect(scope.saveTimer).toEqual(null)
  })

  it('should NOT update note name when updateNoteName() is called with an invalid name', function () {
    spyOn(websocketMsgSrvMock, 'renameNote')
    scope.updateNoteName('')
    expect(scope.note.name).toEqual(noteMock.name)
    expect(websocketMsgSrvMock.renameNote).not.toHaveBeenCalled()
    scope.updateNoteName(' ')
    expect(scope.note.name).toEqual(noteMock.name)
    expect(websocketMsgSrvMock.renameNote).not.toHaveBeenCalled()
    scope.updateNoteName(scope.note.name)
    expect(scope.note.name).toEqual(noteMock.name)
    expect(websocketMsgSrvMock.renameNote).not.toHaveBeenCalled()
  })

  it('should update note name when updateNoteName() is called with a valid name', function () {
    spyOn(websocketMsgSrvMock, 'renameNote')
    let newName = 'Your Note'
    scope.updateNoteName(newName)
    expect(scope.note.name).toEqual(newName)
    expect(websocketMsgSrvMock.renameNote).toHaveBeenCalled()
  })

  it('should reload note info once per one "setNoteMenu" event', function () {
    spyOn(websocketMsgSrvMock, 'getNote')
    spyOn(websocketMsgSrvMock, 'listRevisionHistory')

    scope.$broadcast('setNoteMenu')
    expect(websocketMsgSrvMock.getNote.calls.count()).toEqual(0)
    expect(websocketMsgSrvMock.listRevisionHistory.calls.count()).toEqual(0)

    websocketMsgSrvMock.getNote.calls.reset()
    websocketMsgSrvMock.listRevisionHistory.calls.reset()

    scope.$broadcast('setNoteMenu')
    expect(websocketMsgSrvMock.getNote.calls.count()).toEqual(0)
    expect(websocketMsgSrvMock.listRevisionHistory.calls.count()).toEqual(0)
  })
})
