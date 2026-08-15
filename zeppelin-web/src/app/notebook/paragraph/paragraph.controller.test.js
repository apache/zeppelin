describe('Controller: ParagraphCtrl', function() {
  beforeEach(angular.mock.module('zeppelinWebApp'));

  let scope;
  let rootScope;
  let websocketMsgSrvMock = {};
  let paragraphMock = {
    config: {},
    settings: {
      forms: {},
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
    rootScope = $rootScope;
    $rootScope.notebookScope = $rootScope.$new(true, $rootScope);

    $controller('ParagraphCtrl', {
      $scope: scope,
      websocketMsgSrv: websocketMsgSrvMock,
      $element: {},
      $route: route,
    });

    scope.init(paragraphMock);
  }));

  let functions = ['isRunning', 'getIframeDimensions', 'cancelParagraph', 'runParagraph', 'saveParagraph',
    'moveUp', 'moveDown', 'insertNew', 'removeParagraph', 'toggleEditor', 'closeEditor', 'openEditor',
    'closeTable', 'openTable', 'showTitle', 'hideTitle', 'setTitle', 'showLineNumbers', 'hideLineNumbers',
    'changeColWidth', 'columnWidthClass', 'toggleOutput',
    'aceChanged', 'aceLoaded', 'getEditorValue', 'getProgress', 'getExecutionTime', 'isResultOutdated'];

  functions.forEach(function(fn) {
    it('check for scope functions to be defined : ' + fn, function() {
      expect(scope[fn]).toBeDefined();
    });
  });

  it('should have this array of values for "colWidthOption"', function() {
    expect(scope.colWidthOption).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
  });

  it('should set default value of "paragraphFocused" as false', function() {
    expect(scope.paragraphFocused).toEqual(false);
  });

  describe('completion candidate filtering', function() {
    let FilteredList;
    let originalSetFilter;

    let completionParagraph = {
      id: 'paragraph_completion',
      config: {
        editorSetting: {
          completionSupport: true,
        },
      },
      settings: {
        forms: {},
      },
    };

    let fromInterpreter = function(value, meta) {
      return {value: value, caption: value, meta: meta, score: 300, fromBackend: true};
    };

    let fromAce = function(value, meta) {
      return {value: value, caption: value, meta: meta, score: 0};
    };

    // the filter lives on ace's FilteredList prototype, installed on focus
    let applyFilter = function(candidates) {
      let list = new FilteredList(candidates);
      list.setFilter('');
      return list.filtered;
    };

    beforeEach(function() {
      FilteredList = ace.require('ace/autocomplete').FilteredList;
      originalSetFilter = FilteredList.prototype.setFilter;
      scope.init(completionParagraph);
      rootScope.$broadcast('focusParagraph', completionParagraph.id, 0, 0, true);
    });

    afterEach(function() {
      FilteredList.prototype.setFilter = originalSetFilter;
    });

    it('should keep interpreter candidates that carry a meta label', function() {
      let table = fromInterpreter('my_table', 'table');
      expect(applyFilter([table])).toContain(table);
    });

    it('should hide ace candidates while interpreter candidates are available', function() {
      let table = fromInterpreter('my_table', 'table');
      let local = fromAce('my_local_var', 'local');
      let keyword = fromAce('select', 'keyword');

      let filtered = applyFilter([table, local, keyword]);

      expect(filtered).toContain(table);
      expect(filtered).not.toContain(local);
      expect(filtered).not.toContain(keyword);
    });

    describe('when the interpreter answers with no candidates', function() {
      let editorElement;

      // completionListLength is only settable through a listener aceLoaded registers
      beforeEach(function() {
        websocketMsgSrvMock.getEditorSetting = function() {};
        websocketMsgSrvMock.completion = function() {};

        editorElement = document.createElement('div');
        editorElement.id = 'completion_test_editor';
        document.body.appendChild(editorElement);

        scope.aceLoaded(ace.edit(editorElement));
        rootScope.$broadcast('completionListLength', 0);
      });

      afterEach(function() {
        document.body.removeChild(editorElement);
      });

      it('should fall back to ace candidates', function() {
        let local = fromAce('my_local_var', 'local');
        let keyword = fromAce('select', 'keyword');

        let filtered = applyFilter([local, keyword]);

        expect(filtered).toContain(local);
        expect(filtered).toContain(keyword);
      });
    });

    describe('candidates built from an interpreter answer', function() {
      let editorElement;
      let editor;

      beforeEach(function() {
        websocketMsgSrvMock.getEditorSetting = function() {};
        websocketMsgSrvMock.completion = function() {};

        editorElement = document.createElement('div');
        editorElement.id = 'completion_producer_editor';
        document.body.appendChild(editorElement);

        editor = ace.edit(editorElement);
        scope.aceLoaded(editor);
        editor.focus();
      });

      afterEach(function() {
        document.body.removeChild(editorElement);
      });

      // aceLoaded installs remoteCompleter as the first of ace's completers.
      let collectCandidates = function(completions) {
        let remoteCompleter = editor.completers[0];
        let received = null;

        remoteCompleter.getCompletions(editor, editor.getSession(), {row: 0, column: 0}, '', function(err, items) {
          received = items;
        });
        rootScope.$broadcast('completionList', {completions: completions});

        return received;
      };

      it('should mark every candidate as coming from the interpreter', function() {
        let candidates = collectCandidates([
          {name: 'my_table', value: 'my_table', meta: 'table'},
          {name: 'my_schema', value: 'my_schema', meta: 'schema'},
        ]);

        expect(candidates).not.toBeNull();
        expect(candidates.length).toEqual(2);
        candidates.forEach(function(candidate) {
          expect(candidate.fromBackend).toBe(true);
        });
      });

      it('should produce candidates that survive the filter', function() {
        let candidates = collectCandidates([{name: 'my_table', value: 'my_table', meta: 'table'}]);

        expect(applyFilter(candidates)).toEqual(candidates);
      });
    });
  });
});
