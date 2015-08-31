'use strict';

describe('Controller: ParagraphCtrl', function() {

  beforeEach(module('zeppelinWebApp'));

  var ParagraphCtrl;
  var scope;
  var websocketMsgSrvMock = {};
  var paragraphMock = {
    config: {}
  };

  beforeEach(inject(function($controller, $rootScope) {
    scope = $rootScope.$new();
    ParagraphCtrl = $controller('ParagraphCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      $element: {}
    });
    scope.init(paragraphMock);
  }));

  var functions = ['isRunning', 'getIframeDimensions', 'cancelParagraph', 'runParagraph', 'saveParagraph',
    'moveUp', 'moveDown', 'insertNew', 'removeParagraph', 'toggleEditor', 'closeEditor', 'openEditor',
    'closeTable', 'openTable', 'showTitle', 'hideTitle', 'setTitle', 'showLineNumbers', 'hideLineNumbers',
    'changeColWidth', 'columnWidthClass', 'toggleGraphOption', 'toggleOutput', 'loadForm',
    'aceChanged', 'aceLoaded', 'getEditorValue', 'getProgress', 'getExecutionTime', 'isResultOutdated',
    'getResultType', 'loadTableData', 'setGraphMode', 'isGraphMode', 'onGraphOptionChange',
    'removeGraphOptionKeys', 'removeGraphOptionValues', 'removeGraphOptionGroups', 'setGraphOptionValueAggr',
    'removeScatterOptionXaxis', 'removeScatterOptionYaxis', 'removeScatterOptionGroup',
    'removeScatterOptionSize'];

  functions.forEach(function(fn) {
    it('check for scope functions to be defined : ' + fn, function() {
      expect(scope[fn]).toBeDefined();
    });
  });

  it('getResultType should return "TEXT" when the type is unknown', function() {
    expect(scope.getResultType()).toEqual('TEXT');
  });

  it('init value for colWidthOption should be false', function() {
    expect(scope.colWidthOption).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  });

  it('init value for paragraphFocused should be false', function() {
    expect(scope.paragraphFocused).toEqual(false);
  });

  it('loadTableData should be called when the result type is "TABLE" and the Graph mode should be "table"', function() {
    scope.getResultType = jasmine.createSpy('getResultType spy').andCallFake(function() {
      return 'TABLE';
    });
    spyOn(scope, 'loadTableData');
    spyOn(scope, 'setGraphMode');
    scope.init(paragraphMock);
    expect(scope.loadTableData).toHaveBeenCalled();
    expect(scope.setGraphMode).toHaveBeenCalled();
    expect(scope.getGraphMode()).toEqual('table');
  });

  it('renderHtml should be called when the result type is "HTML"', function() {
    scope.getResultType = jasmine.createSpy('getResultType spy').andCallFake(function() {
      return 'HTML';
    });
    spyOn(scope, 'renderHtml');
    scope.init(paragraphMock);
    expect(scope.renderHtml).toHaveBeenCalled();
  });

  it('renderAngular should be called when the result type is "ANGULAR"', function() {
    scope.getResultType = jasmine.createSpy('getResultType spy').andCallFake(function() {
      return 'ANGULAR';
    });
    spyOn(scope, 'renderAngular');
    scope.init(paragraphMock);
    expect(scope.renderAngular).toHaveBeenCalled();
  });

});
