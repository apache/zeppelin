describe('Controller: ParagraphCtrl', function () {
  beforeEach(angular.mock.module('zeppelinWebApp'))

  let scope
  let websocketMsgSrvMock = {}
  let paragraphMock = {
    config: {},
    settings: {
      forms: {}
    }
  }
  let route = {
    current: {
      pathParams: {
        noteId: 'noteId'
      }
    }
  }

  beforeEach(inject(function ($controller, $rootScope) {
    scope = $rootScope.$new()
    $rootScope.notebookScope = $rootScope.$new(true, $rootScope)

    $controller('ParagraphCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      $element: {},
      $route: route
    })

    scope.init(paragraphMock)
  }))

  let functions = ['isRunning', 'getIframeDimensions', 'cancelParagraph', 'runParagraph', 'saveParagraph',
    'moveUp', 'moveDown', 'insertNew', 'removeParagraph', 'toggleEditor', 'closeEditor', 'openEditor',
    'closeTable', 'openTable', 'showTitle', 'hideTitle', 'setTitle', 'showLineNumbers', 'hideLineNumbers',
    'changeColWidth', 'columnWidthClass', 'toggleOutput',
    'aceChanged', 'aceLoaded', 'getEditorValue', 'getProgress', 'getExecutionTime', 'isResultOutdated']

  functions.forEach(function (fn) {
    it('check for scope functions to be defined : ' + fn, function () {
      expect(scope[fn]).toBeDefined()
    })
  })

  it('should have this array of values for "colWidthOption"', function () {
    expect(scope.colWidthOption).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
  })

  it('should set default value of "paragraphFocused" as false', function () {
    expect(scope.paragraphFocused).toEqual(false)
  })
})
