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

  describe('copyToClipboard', function() {
    let tableResultMock;
    let tableConfigMock;
    let tableParagraphMock;
    let clipboardText;

    beforeEach(inject(function($controller, $rootScope) {
      tableResultMock = {
        type: 'TABLE',
        data: 'name\tcount\na\t12\nb\t24\n',
      };
      tableConfigMock = {
        graph: {
          mode: 'table',
          height: 300,
          optionOpen: false,
          setting: {},
        },
      };
      tableParagraphMock = {
        id: 'p2',
        results: {
          msg: [tableResultMock],
        },
      };

      scope = $rootScope.$new();
      scope.$parent = $rootScope.$new(true, $rootScope);
      scope.$parent.paragraph = tableParagraphMock;

      controller = $controller('ResultCtrl', {
        $scope: scope,
        $route: route,
      });

      scope.init(tableResultMock, tableConfigMock, tableParagraphMock, 0);

      clipboardText = null;
      spyOn(navigator.clipboard, 'writeText').and.callFake(function(text) {
        clipboardText = text;
        return Promise.resolve();
      });
    }));

    it('should copy TSV with header row to clipboard', function(done) {
      scope.copyToClipboard('\t');
      setTimeout(function() {
        expect(navigator.clipboard.writeText).toHaveBeenCalled();
        let lines = clipboardText.split('\n').filter(function(l) {
          return l.length > 0;
        });
        expect(lines[0]).toBe('name\tcount');
        expect(lines[1]).toBe('a\t12');
        expect(lines[2]).toBe('b\t24');
        done();
      }, 0);
    });

    it('should copy CSV with header row to clipboard', function(done) {
      scope.copyToClipboard(',');
      setTimeout(function() {
        expect(navigator.clipboard.writeText).toHaveBeenCalled();
        let lines = clipboardText.split('\n').filter(function(l) {
          return l.length > 0;
        });
        expect(lines[0]).toBe('name,count');
        expect(lines[1]).toBe('a,12');
        done();
      }, 0);
    });

    it('should quote cell values that contain the delimiter', function(done) {
      let specialResultMock = {
        type: 'TABLE',
        data: 'col1\tcol2\nhello,world\t42\n',
      };
      let specialParagraphMock = {
        id: 'p3',
        results: {
          msg: [specialResultMock],
        },
      };

      inject(function($controller, $rootScope) {
        let specialScope = $rootScope.$new();
        specialScope.$parent = $rootScope.$new(true, $rootScope);
        specialScope.$parent.paragraph = specialParagraphMock;
        $controller('ResultCtrl', {$scope: specialScope, $route: route});
        specialScope.init(specialResultMock, tableConfigMock, specialParagraphMock, 0);

        spyOn(navigator.clipboard, 'writeText').and.callFake(function(text) {
          clipboardText = text;
          return Promise.resolve();
        });

        specialScope.copyToClipboard(',');
        setTimeout(function() {
          let lines = clipboardText.split('\n').filter(function(l) {
            return l.length > 0;
          });
          // "hello,world" contains comma — must be quoted for CSV
          expect(lines[1]).toBe('"hello,world",42');
          done();
        }, 0);
      });
    });

    it('should quote cell values that contain double quotes', function(done) {
      let quoteResultMock = {
        type: 'TABLE',
        data: 'col1\tcol2\nsay "hi"\t1\n',
      };
      let quoteParagraphMock = {
        id: 'p4',
        results: {
          msg: [quoteResultMock],
        },
      };

      inject(function($controller, $rootScope) {
        let quoteScope = $rootScope.$new();
        quoteScope.$parent = $rootScope.$new(true, $rootScope);
        quoteScope.$parent.paragraph = quoteParagraphMock;
        $controller('ResultCtrl', {$scope: quoteScope, $route: route});
        quoteScope.init(quoteResultMock, tableConfigMock, quoteParagraphMock, 0);

        spyOn(navigator.clipboard, 'writeText').and.callFake(function(text) {
          clipboardText = text;
          return Promise.resolve();
        });

        quoteScope.copyToClipboard('\t');
        setTimeout(function() {
          let lines = clipboardText.split('\n').filter(function(l) {
            return l.length > 0;
          });
          expect(lines[1]).toBe('"say ""hi"""\t1');
          done();
        }, 0);
      });
    });
  });
});
