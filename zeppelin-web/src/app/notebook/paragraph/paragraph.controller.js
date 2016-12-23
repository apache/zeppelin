/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';
(function() {

  angular.module('zeppelinWebApp').controller('ParagraphCtrl', ParagraphCtrl);

  ParagraphCtrl.$inject = [
    '$scope',
    '$rootScope',
    '$route',
    '$window',
    '$routeParams',
    '$location',
    '$timeout',
    '$compile',
    '$http',
    '$q',
    'websocketMsgSrv',
    'baseUrlSrv',
    'ngToast',
    'saveAsService',
    'noteVarShareService'
  ];

  function ParagraphCtrl($scope, $rootScope, $route, $window, $routeParams, $location,
                         $timeout, $compile, $http, $q, websocketMsgSrv,
                         baseUrlSrv, ngToast, saveAsService, noteVarShareService) {
    var ANGULAR_FUNCTION_OBJECT_NAME_PREFIX = '_Z_ANGULAR_FUNC_';
    $scope.parentNote = null;
    $scope.paragraph = null;
    $scope.originalText = '';
    $scope.editor = null;

    var editorSetting = {};
    var pastePercentSign = false;
    var paragraphScope = $rootScope.$new(true, $rootScope);

    // to keep backward compatibility
    $scope.compiledScope = paragraphScope;

    paragraphScope.z = {
      // z.runParagraph('20150213-231621_168813393')
      runParagraph: function(paragraphId) {
        if (paragraphId) {
          var filtered = $scope.parentNote.paragraphs.filter(function(x) {
            return x.id === paragraphId;});
          if (filtered.length === 1) {
            var paragraph = filtered[0];
            websocketMsgSrv.runParagraph(paragraph.id, paragraph.title, paragraph.text,
                paragraph.config, paragraph.settings.params);
          } else {
            ngToast.danger({content: 'Cannot find a paragraph with id \'' + paragraphId + '\'',
              verticalPosition: 'top', dismissOnTimeout: false});
          }
        } else {
          ngToast.danger({
            content: 'Please provide a \'paragraphId\' when calling z.runParagraph(paragraphId)',
            verticalPosition: 'top', dismissOnTimeout: false});
        }
      },

      // Example: z.angularBind('my_var', 'Test Value', '20150213-231621_168813393')
      angularBind: function(varName, value, paragraphId) {
        // Only push to server if there paragraphId is defined
        if (paragraphId) {
          websocketMsgSrv.clientBindAngularObject($routeParams.noteId, varName, value, paragraphId);
        } else {
          ngToast.danger({
            content: 'Please provide a \'paragraphId\' when calling ' +
            'z.angularBind(varName, value, \'PUT_HERE_PARAGRAPH_ID\')',
            verticalPosition: 'top', dismissOnTimeout: false});
        }
      },

      // Example: z.angularUnBind('my_var', '20150213-231621_168813393')
      angularUnbind: function(varName, paragraphId) {
        // Only push to server if paragraphId is defined
        if (paragraphId) {
          websocketMsgSrv.clientUnbindAngularObject($routeParams.noteId, varName, paragraphId);
        } else {
          ngToast.danger({
            content: 'Please provide a \'paragraphId\' when calling ' +
            'z.angularUnbind(varName, \'PUT_HERE_PARAGRAPH_ID\')',
            verticalPosition: 'top', dismissOnTimeout: false});
        }
      }
    };

    var angularObjectRegistry = {};

    // Controller init
    $scope.init = function(newParagraph, note) {
      $scope.paragraph = newParagraph;
      $scope.parentNote = note;
      $scope.originalText = angular.copy(newParagraph.text);
      $scope.chart = {};
      $scope.baseMapOption = ['Streets', 'Satellite', 'Hybrid', 'Topo', 'Gray', 'Oceans', 'Terrain'];
      $scope.colWidthOption = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
      $scope.paragraphFocused = false;
      if (newParagraph.focus) {
        $scope.paragraphFocused = true;
      }
      if (!$scope.paragraph.config) {
        $scope.paragraph.config = {};
      }

      noteVarShareService.put($scope.paragraph.id + '_paragraphScope', paragraphScope);

      initializeDefault($scope.paragraph.config);
    };

    var initializeDefault = function(config) {
      if (!config.colWidth) {
        config.colWidth = 12;
      }

      if (config.enabled === undefined) {
        config.enabled = true;
      }

      if (!config.results) {
        config.results = {};
      }

      if (!config.editorSetting) {
        config.editorSetting = {};
      } else if (config.editorSetting.editOnDblClick) {
        editorSetting.isOutputHidden = config.editorSetting.editOnDblClick;
      }
    };

    $scope.$on('updateParagraphOutput', function(event, data) {
      if ($scope.paragraph.id === data.paragraphId) {
        if (!$scope.paragraph.results) {
          $scope.paragraph.results = {};
        }
        if (!$scope.paragraph.results.msg) {
          $scope.paragraph.results.msg = [];
        }

        var update = ($scope.paragraph.results.msg[data.index]) ? true : false;

        $scope.paragraph.results.msg[data.index] = {
          data: data.data,
          type: data.type
        };

        if (update) {
          $rootScope.$broadcast(
            'updateResult',
            $scope.paragraph.results.msg[data.index],
            $scope.paragraph.config.results[data.index],
            $scope.paragraph,
            data.index);
        }
      }
    });

    $scope.getIframeDimensions = function() {
      if ($scope.asIframe) {
        var paragraphid = '#' + $routeParams.paragraphId + '_container';
        var height = angular.element(paragraphid).height();
        return height;
      }
      return 0;
    };

    $scope.$watch($scope.getIframeDimensions, function(newValue, oldValue) {
      if ($scope.asIframe && newValue) {
        var message = {};
        message.height = newValue;
        message.url = $location.$$absUrl;
        $window.parent.postMessage(angular.toJson(message), '*');
      }
    });

    var isEmpty = function(object) {
      return !object;
    };

    $scope.isRunning = function(paragraph) {
      return paragraph.status === 'RUNNING' || paragraph.status === 'PENDING';
    };

    $scope.cancelParagraph = function(paragraph) {
      console.log('Cancel %o', paragraph.id);
      websocketMsgSrv.cancelParagraphRun(paragraph.id);
    };

    $scope.runParagraph = function(data) {
      websocketMsgSrv.runParagraph($scope.paragraph.id, $scope.paragraph.title,
                                   data, $scope.paragraph.config, $scope.paragraph.settings.params);
      $scope.originalText = angular.copy(data);
      $scope.dirtyText = undefined;

      if ($scope.paragraph.config.editorSetting.editOnDblClick) {
        closeEditorAndOpenTable($scope.paragraph);
      } else if (editorSetting.isOutputHidden &&
          !$scope.paragraph.config.editorSetting.editOnDblClick) {
        // %md/%angular repl make output to be hidden by default after running
        // so should open output if repl changed from %md/%angular to another
        openEditorAndOpenTable($scope.paragraph);
      }
      editorSetting.isOutputHidden = $scope.paragraph.config.editorSetting.editOnDblClick;
    };

    $scope.saveParagraph = function(paragraph) {
      const dirtyText = paragraph.text;
      if (dirtyText === undefined || dirtyText === $scope.originalText) {
        return;
      }
      commitParagraph(paragraph);
      $scope.originalText = dirtyText;
      $scope.dirtyText = undefined;
    };

    $scope.toggleEnableDisable = function(paragraph) {
      paragraph.config.enabled = !paragraph.config.enabled;
      commitParagraph(paragraph);
    };

    $scope.run = function(paragraph, editorValue) {
      if (editorValue && !$scope.isRunning(paragraph)) {
        $scope.runParagraph(editorValue);
      }
    };

    $scope.moveUp = function(paragraph) {
      $scope.$emit('moveParagraphUp', paragraph);
    };

    $scope.moveDown = function(paragraph) {
      $scope.$emit('moveParagraphDown', paragraph);
    };

    $scope.insertNew = function(position) {
      $scope.$emit('insertParagraph', $scope.paragraph.id, position);
    };

    $scope.copyPara = function(position) {
      var editorValue = $scope.editor.getValue();
      if (editorValue) {
        $scope.copyParagraph(editorValue, position);
      }
    };

    $scope.copyParagraph = function(data, position) {
      var newIndex = -1;
      for (var i = 0; i < $scope.note.paragraphs.length; i++) {
        if ($scope.note.paragraphs[i].id === $scope.paragraph.id) {
          //determine position of where to add new paragraph; default is below
          if (position === 'above') {
            newIndex = i;
          } else {
            newIndex = i + 1;
          }
          break;
        }
      }

      if (newIndex < 0 || newIndex > $scope.note.paragraphs.length) {
        return;
      }

      var config = angular.copy($scope.paragraph.config);
      config.editorHide = false;

      websocketMsgSrv.copyParagraph(newIndex, $scope.paragraph.title, data,
                                        config, $scope.paragraph.settings.params);
    };

    $scope.removeParagraph = function(paragraph) {
      var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
      if (paragraphs[paragraphs.length - 1].id.indexOf(paragraph.id) === 0) {
        BootstrapDialog.alert({
          closable: true,
          message: 'The last paragraph can\'t be deleted.',
          callback: function(result) {
            if (result) {
              $scope.editor.focus();
            }
          }
        });
      } else {
        BootstrapDialog.confirm({
          closable: true,
          title: '',
          message: 'Do you want to delete this paragraph?',
          callback: function(result) {
            if (result) {
              console.log('Remove paragraph');
              websocketMsgSrv.removeParagraph(paragraph.id);
              $scope.$emit('moveFocusToNextParagraph', $scope.paragraph.id);
            }
          }
        });
      }
    };

    $scope.clearParagraphOutput = function(paragraph) {
      websocketMsgSrv.clearParagraphOutput(paragraph.id);
    };

    $scope.toggleEditor = function(paragraph) {
      if (paragraph.config.editorHide) {
        $scope.openEditor(paragraph);
      } else {
        $scope.closeEditor(paragraph);
      }
    };

    $scope.closeEditor = function(paragraph) {
      console.log('close the note');
      paragraph.config.editorHide = true;
      commitParagraph(paragraph);
    };

    $scope.openEditor = function(paragraph) {
      console.log('open the note');
      paragraph.config.editorHide = false;
      commitParagraph(paragraph);
    };

    $scope.closeTable = function(paragraph) {
      console.log('close the output');
      paragraph.config.tableHide = true;
      commitParagraph(paragraph);
    };

    $scope.openTable = function(paragraph) {
      console.log('open the output');
      paragraph.config.tableHide = false;
      commitParagraph(paragraph);
    };

    var openEditorAndCloseTable = function(paragraph) {
      manageEditorAndTableState(paragraph, false, true);
    };

    var closeEditorAndOpenTable = function(paragraph) {
      manageEditorAndTableState(paragraph, true, false);
    };

    var openEditorAndOpenTable = function(paragraph) {
      manageEditorAndTableState(paragraph, false, false);
    };

    var manageEditorAndTableState = function(paragraph, hideEditor, hideTable) {
      paragraph.config.editorHide = hideEditor;
      paragraph.config.tableHide = hideTable;
      commitParagraph(paragraph);
    };

    $scope.showTitle = function(paragraph) {
      paragraph.config.title = true;
      commitParagraph(paragraph);
    };

    $scope.hideTitle = function(paragraph) {
      paragraph.config.title = false;
      commitParagraph(paragraph);
    };

    $scope.setTitle = function(paragraph) {
      commitParagraph(paragraph);
    };

    $scope.showLineNumbers = function(paragraph) {
      paragraph.config.lineNumbers = true;
      $scope.editor.renderer.setShowGutter(true);
      commitParagraph(paragraph);
    };

    $scope.hideLineNumbers = function(paragraph) {
      paragraph.config.lineNumbers = false;
      $scope.editor.renderer.setShowGutter(false);
      commitParagraph(paragraph);
    };

    $scope.columnWidthClass = function(n) {
      if ($scope.asIframe) {
        return 'col-md-12';
      } else {
        return 'col-md-' + n;
      }
    };

    $scope.changeColWidth = function(paragraph, width) {
      angular.element('.navbar-right.open').removeClass('open');
      if (width !== paragraph.config.colWidth) {
        paragraph.config.colWidth = width;
        commitParagraph(paragraph);
      }
    };

    $scope.toggleOutput = function(paragraph) {
      paragraph.config.tableHide = !paragraph.config.tableHide;
      commitParagraph(paragraph);
    };

    $scope.loadForm = function(formulaire, params) {
      var value = formulaire.defaultValue;
      if (params[formulaire.name]) {
        value = params[formulaire.name];
      }

      $scope.paragraph.settings.params[formulaire.name] = value;
    };

    $scope.toggleCheckbox = function(formulaire, option) {
      var idx = $scope.paragraph.settings.params[formulaire.name].indexOf(option.value);
      if (idx > -1) {
        $scope.paragraph.settings.params[formulaire.name].splice(idx, 1);
      } else {
        $scope.paragraph.settings.params[formulaire.name].push(option.value);
      }
    };

    $scope.aceChanged = function(_, editor) {
      var session = editor.getSession();
      var dirtyText = session.getValue();
      $scope.dirtyText = dirtyText;
      $scope.startSaveTimer();
      setParagraphMode(session, dirtyText, editor.getCursorPosition());
    };

    $scope.aceLoaded = function(_editor) {
      var langTools = ace.require('ace/ext/language_tools');
      var Range = ace.require('ace/range').Range;

      _editor.$blockScrolling = Infinity;
      $scope.editor = _editor;
      $scope.editor.on('input', $scope.aceChanged);
      if (_editor.container.id !== '{{paragraph.id}}_editor') {
        $scope.editor.renderer.setShowGutter($scope.paragraph.config.lineNumbers);
        $scope.editor.setShowFoldWidgets(false);
        $scope.editor.setHighlightActiveLine(false);
        $scope.editor.setHighlightGutterLine(false);
        $scope.editor.getSession().setUseWrapMode(true);
        $scope.editor.setTheme('ace/theme/chrome');
        $scope.editor.setReadOnly($scope.isRunning($scope.paragraph));
        if ($scope.paragraphFocused) {
          $scope.editor.focus();
          $scope.goToEnd($scope.editor);
        }

        autoAdjustEditorHeight(_editor);
        angular.element(window).resize(function() {
          autoAdjustEditorHeight(_editor);
        });

        if (navigator.appVersion.indexOf('Mac') !== -1) {
          $scope.editor.setKeyboardHandler('ace/keyboard/emacs');
          $rootScope.isMac = true;
        } else if (navigator.appVersion.indexOf('Win') !== -1 ||
                   navigator.appVersion.indexOf('X11') !== -1 ||
                   navigator.appVersion.indexOf('Linux') !== -1) {
          $rootScope.isMac = false;
          // not applying emacs key binding while the binding override Ctrl-v. default behavior of paste text on windows.
        }

        var remoteCompleter = {
          getCompletions: function(editor, session, pos, prefix, callback) {
            if (!editor.isFocused()) {
              return;
            }

            pos = session.getTextRange(new Range(0, 0, pos.row, pos.column)).length;
            var buf = session.getValue();

            websocketMsgSrv.completion($scope.paragraph.id, buf, pos);

            $scope.$on('completionList', function(event, data) {
              if (data.completions) {
                var completions = [];
                for (var c in data.completions) {
                  var v = data.completions[c];
                  completions.push({
                    name: v.name,
                    value: v.value,
                    score: 300
                  });
                }
                callback(null, completions);
              }
            });
          }
        };

        langTools.setCompleters([remoteCompleter, langTools.keyWordCompleter, langTools.snippetCompleter,
          langTools.textCompleter]);

        $scope.editor.setOptions({
          enableBasicAutocompletion: true,
          enableSnippets: false,
          enableLiveAutocompletion: false
        });

        $scope.handleFocus = function(value, isDigestPass) {
          $scope.paragraphFocused = value;
          if (isDigestPass === false || isDigestPass === undefined) {
            // Protect against error in case digest is already running
            $timeout(function() {
              // Apply changes since they come from 3rd party library
              $scope.$digest();
            });
          }
        };

        $scope.editor.on('focus', function() {
          $scope.handleFocus(true);
        });

        $scope.editor.on('blur', function() {
          $scope.handleFocus(false);
        });

        $scope.editor.on('paste', function(e) {
          if (e.text.indexOf('%') === 0) {
            pastePercentSign = true;
          }
        });

        $scope.editor.getSession().on('change', function(e, editSession) {
          autoAdjustEditorHeight(_editor);
        });

        setParagraphMode($scope.editor.getSession(), $scope.editor.getSession().getValue());

        // autocomplete on '.'
        /*
        $scope.editor.commands.on("afterExec", function(e, t) {
          if (e.command.name == "insertstring" && e.args == "." ) {
        var all = e.editor.completers;
        //e.editor.completers = [remoteCompleter];
        e.editor.execCommand("startAutocomplete");
        //e.editor.completers = all;
      }
        });
        */

        // remove binding
        $scope.editor.commands.bindKey('ctrl-alt-n.', null);
        $scope.editor.commands.removeCommand('showSettingsMenu');

        $scope.editor.commands.bindKey('ctrl-alt-l', null);
        $scope.editor.commands.bindKey('ctrl-alt-w', null);

        // autocomplete on 'ctrl+.'
        $scope.editor.commands.bindKey('ctrl-.', 'startAutocomplete');
        $scope.editor.commands.bindKey('ctrl-space', null);

        var keyBindingEditorFocusAction = function(scrollValue) {
          var numRows = $scope.editor.getSession().getLength();
          var currentRow = $scope.editor.getCursorPosition().row;
          if (currentRow === 0 && scrollValue <= 0) {
            // move focus to previous paragraph
            $scope.$emit('moveFocusToPreviousParagraph', $scope.paragraph.id);
          } else if (currentRow === numRows - 1 && scrollValue >= 0) {
            $scope.$emit('moveFocusToNextParagraph', $scope.paragraph.id);
          } else {
            $scope.scrollToCursor($scope.paragraph.id, scrollValue);
          }
        };

        // handle cursor moves
        $scope.editor.keyBinding.origOnCommandKey = $scope.editor.keyBinding.onCommandKey;
        $scope.editor.keyBinding.onCommandKey = function(e, hashId, keyCode) {
          if ($scope.editor.completer && $scope.editor.completer.activated) { // if autocompleter is active
          } else {
            // fix ace editor focus issue in chrome (textarea element goes to top: -1000px after focused by cursor move)
            if (parseInt(angular.element('#' + $scope.paragraph.id + '_editor > textarea')
                .css('top').replace('px', '')) < 0) {
              var position = $scope.editor.getCursorPosition();
              var cursorPos = $scope.editor.renderer.$cursorLayer.getPixelPosition(position, true);
              angular.element('#' + $scope.paragraph.id + '_editor > textarea').css('top', cursorPos.top);
            }

            var ROW_UP = -1;
            var ROW_DOWN = 1;

            switch (keyCode) {
              case 38:
                keyBindingEditorFocusAction(ROW_UP);
                break;
              case 80:
                if (e.ctrlKey && !e.altKey) {
                  keyBindingEditorFocusAction(ROW_UP);
                }
                break;
              case 40:
                keyBindingEditorFocusAction(ROW_DOWN);
                break;
              case 78:
                if (e.ctrlKey && !e.altKey) {
                  keyBindingEditorFocusAction(ROW_DOWN);
                }
                break;
            }
          }
          this.origOnCommandKey(e, hashId, keyCode);
        };
      }
    };

    var getEditorSetting = function(paragraph, interpreterName) {
      var deferred = $q.defer();
      websocketMsgSrv.getEditorSetting(paragraph.id, interpreterName);
      $timeout(
        $scope.$on('editorSetting', function(event, data) {
          if (paragraph.id === data.paragraphId) {
            deferred.resolve(data);
          }
        }
      ), 1000);
      return deferred.promise;
    };

    var setEditorLanguage = function(session, language) {
      var mode = 'ace/mode/';
      mode += language;
      $scope.paragraph.config.editorMode = mode;
      session.setMode(mode);
    };

    var setParagraphMode = function(session, paragraphText, pos) {
      // Evaluate the mode only if the the position is undefined
      // or the first 30 characters of the paragraph have been modified
      // or cursor position is at beginning of second line.(in case user hit enter after typing %magic)
      if ((typeof pos === 'undefined') || (pos.row === 0 && pos.column < 30) ||
          (pos.row === 1 && pos.column === 0) || pastePercentSign) {
        // If paragraph loading, use config value if exists
        if ((typeof pos === 'undefined') && $scope.paragraph.config.editorMode) {
          session.setMode($scope.paragraph.config.editorMode);
        } else {
          var magic = getInterpreterName(paragraphText);
          if (editorSetting.magic !== magic) {
            editorSetting.magic = magic;
            getEditorSetting($scope.paragraph, magic)
              .then(function(setting) {
                setEditorLanguage(session, setting.editor.language);
                _.merge($scope.paragraph.config.editorSetting, setting.editor);
              });
          }
        }
      }
      pastePercentSign = false;
    };

    var getInterpreterName = function(paragraphText) {
      var intpNameRegexp = /^\s*%(.+?)\s/g;
      var match = intpNameRegexp.exec(paragraphText);
      if (match) {
        return match[1].trim();
      // get default interpreter name if paragraph text doesn't start with '%'
      // TODO(mina): dig into the cause what makes interpreterBindings to have no element
      } else if ($scope.$parent.interpreterBindings.length !== 0) {
        return $scope.$parent.interpreterBindings[0].name;
      }
      return '';
    };

    var autoAdjustEditorHeight = function(editor) {
      var height =
        editor.getSession().getScreenLength() *
        editor.renderer.lineHeight +
        editor.renderer.scrollBar.getWidth();

      angular.element('#' + editor.container.id).height(height.toString() + 'px');
      editor.resize();
    };

    $rootScope.$on('scrollToCursor', function(event) {
      // scroll on 'scrollToCursor' event only when cursor is in the last paragraph
      var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
      if (paragraphs[paragraphs.length - 1].id.indexOf($scope.paragraph.id) === 0) {
        $scope.scrollToCursor($scope.paragraph.id, 0);
      }
    });

    /** scrollToCursor if it is necessary
     * when cursor touches scrollTriggerEdgeMargin from the top (or bottom) of the screen, it autoscroll to place cursor around 1/3 of screen height from the top (or bottom)
     * paragraphId : paragraph that has active cursor
     * lastCursorMove : 1(down), 0, -1(up) last cursor move event
     **/
    $scope.scrollToCursor = function(paragraphId, lastCursorMove) {
      if (!$scope.editor.isFocused()) {
        // only make sense when editor is focused
        return;
      }
      var lineHeight = $scope.editor.renderer.lineHeight;
      var headerHeight = 103; // menubar, notebook titlebar
      var scrollTriggerEdgeMargin = 50;

      var documentHeight = angular.element(document).height();
      var windowHeight = angular.element(window).height();  // actual viewport height

      var scrollPosition = angular.element(document).scrollTop();
      var editorPosition = angular.element('#' + paragraphId + '_editor').offset();
      var position = $scope.editor.getCursorPosition();
      var lastCursorPosition = $scope.editor.renderer.$cursorLayer.getPixelPosition(position, true);

      var calculatedCursorPosition = editorPosition.top + lastCursorPosition.top + lineHeight * lastCursorMove;

      var scrollTargetPos;
      if (calculatedCursorPosition < scrollPosition + headerHeight + scrollTriggerEdgeMargin) {
        scrollTargetPos = calculatedCursorPosition - headerHeight - ((windowHeight - headerHeight) / 3);
        if (scrollTargetPos < 0) {
          scrollTargetPos = 0;
        }
      } else if (calculatedCursorPosition > scrollPosition + scrollTriggerEdgeMargin + windowHeight - headerHeight) {
        scrollTargetPos = calculatedCursorPosition - headerHeight - ((windowHeight - headerHeight) * 2 / 3);

        if (scrollTargetPos > documentHeight) {
          scrollTargetPos = documentHeight;
        }
      }

      // cancel previous scroll animation
      var bodyEl = angular.element('body');
      bodyEl.stop();
      bodyEl.finish();

      // scroll to scrollTargetPos
      bodyEl.scrollTo(scrollTargetPos, {axis: 'y', interrupt: true, duration: 100});
    };

    $scope.getEditorValue = function() {
      return $scope.editor.getValue();
    };

    $scope.getProgress = function() {
      return $scope.currentProgress || 0;
    };

    $scope.getExecutionTime = function(pdata) {
      var timeMs = Date.parse(pdata.dateFinished) - Date.parse(pdata.dateStarted);
      if (isNaN(timeMs) || timeMs < 0) {
        if ($scope.isResultOutdated(pdata)) {
          return 'outdated';
        }
        return '';
      }
      var user = (pdata.user === undefined || pdata.user === null) ? 'anonymous' : pdata.user;
      var desc = 'Took ' + moment.duration((timeMs / 1000), 'seconds').format('h [hrs] m [min] s [sec]') +
        '. Last updated by ' + user + ' at ' + moment(pdata.dateFinished).format('MMMM DD YYYY, h:mm:ss A') + '.';
      if ($scope.isResultOutdated(pdata)) {
        desc += ' (outdated)';
      }
      return desc;
    };

    $scope.getElapsedTime = function(paragraph) {
      return 'Started ' + moment(paragraph.dateStarted).fromNow() + '.';
    };

    $scope.isResultOutdated = function(pdata) {
      if (pdata.dateUpdated !== undefined && Date.parse(pdata.dateUpdated) > Date.parse(pdata.dateStarted)) {
        return true;
      }
      return false;
    };

    $scope.goToEnd = function(editor) {
      editor.navigateFileEnd();
    };

    $scope.getResultType = function(paragraph) {
      var pdata = (paragraph) ? paragraph : $scope.paragraph;
      if (pdata.results && pdata.results.type) {
        return pdata.results.type;
      } else {
        return 'TEXT';
      }
    };

    $scope.parseTableCell = function(cell) {
      if (!isNaN(cell)) {
        if (cell.length === 0 || Number(cell) > Number.MAX_SAFE_INTEGER || Number(cell) < Number.MIN_SAFE_INTEGER) {
          return cell;
        } else {
          return Number(cell);
        }
      }
      var d = moment(cell);
      if (d.isValid()) {
        return d;
      }
      return cell;
    };

    var commitParagraph = function(paragraph) {
      const {
        id,
        title,
        text,
        config,
        settings: {params},
      } = paragraph;

      websocketMsgSrv.commitParagraph(id, title, text, config, params);
    };

    /** Utility function */
    $scope.goToSingleParagraph = function() {
      var noteId = $route.current.pathParams.noteId;
      var redirectToUrl = location.protocol + '//' + location.host + location.pathname + '#/notebook/' + noteId +
        '/paragraph/' + $scope.paragraph.id + '?asIframe';
      $window.open(redirectToUrl);
    };

    $scope.showScrollDownIcon = function(id) {
      var doc = angular.element('#p' + id + '_text');
      if (doc[0]) {
        return doc[0].scrollHeight > doc.innerHeight();
      }
      return false;
    };

    $scope.scrollParagraphDown = function(id) {
      var doc = angular.element('#p' + id + '_text');
      doc.animate({scrollTop: doc[0].scrollHeight}, 500);
      $scope.keepScrollDown = true;
    };

    $scope.showScrollUpIcon = function(id) {
      if (angular.element('#p' + id + '_text')[0]) {
        return angular.element('#p' + id + '_text')[0].scrollTop !== 0;
      }
      return false;

    };

    $scope.scrollParagraphUp = function(id) {
      var doc = angular.element('#p' + id + '_text');
      doc.animate({scrollTop: 0}, 500);
      $scope.keepScrollDown = false;
    };

    $scope.$on('angularObjectUpdate', function(event, data) {
      var noteId = $route.current.pathParams.noteId;
      if (!data.noteId || data.noteId === noteId) {
        var scope;
        var registry;

        if (!data.paragraphId || data.paragraphId === $scope.paragraph.id) {
          scope = paragraphScope;
          registry = angularObjectRegistry;
        } else {
          return;
        }
        var varName = data.angularObject.name;

        if (angular.equals(data.angularObject.object, scope[varName])) {
          // return when update has no change
          return;
        }

        if (!registry[varName]) {
          registry[varName] = {
            interpreterGroupId: data.interpreterGroupId,
            noteId: data.noteId,
            paragraphId: data.paragraphId
          };
        } else {
          registry[varName].noteId = registry[varName].noteId || data.noteId;
          registry[varName].paragraphId = registry[varName].paragraphId || data.paragraphId;
        }

        registry[varName].skipEmit = true;

        if (!registry[varName].clearWatcher) {
          registry[varName].clearWatcher = scope.$watch(varName, function(newValue, oldValue) {
            console.log('angular object (paragraph) updated %o %o', varName, registry[varName]);
            if (registry[varName].skipEmit) {
              registry[varName].skipEmit = false;
              return;
            }
            websocketMsgSrv.updateAngularObject(
              registry[varName].noteId,
              registry[varName].paragraphId,
              varName,
              newValue,
              registry[varName].interpreterGroupId);
          });
        }
        console.log('angular object (paragraph) created %o', varName);
        scope[varName] = data.angularObject.object;

        // create proxy for AngularFunction
        if (varName.indexOf(ANGULAR_FUNCTION_OBJECT_NAME_PREFIX) === 0) {
          var funcName = varName.substring((ANGULAR_FUNCTION_OBJECT_NAME_PREFIX).length);
          scope[funcName] = function() {
            scope[varName] = arguments;
            console.log('angular function (paragraph) invoked %o', arguments);
          };

          console.log('angular function (paragraph) created %o', scope[funcName]);
        }
      }
    });

    $scope.$on('angularObjectRemove', function(event, data) {
      var noteId = $route.current.pathParams.noteId;
      if (!data.noteId || data.noteId === noteId) {
        var scope;
        var registry;

        if (!data.paragraphId || data.paragraphId === $scope.paragraph.id) {
          scope = paragraphScope;
          registry = angularObjectRegistry;
        } else {
          return;
        }

        var varName = data.name;

        // clear watcher
        if (registry[varName]) {
          registry[varName].clearWatcher();
          registry[varName] = undefined;
        }

        // remove scope variable
        scope[varName] = undefined;

        // remove proxy for AngularFunction
        if (varName.indexOf(ANGULAR_FUNCTION_OBJECT_NAME_PREFIX) === 0) {
          var funcName = varName.substring((ANGULAR_FUNCTION_OBJECT_NAME_PREFIX).length);
          scope[funcName] = undefined;
        }
      }
    });

    $scope.$on('updateParagraph', function(event, data) {
      if (data.paragraph.id === $scope.paragraph.id &&
          (data.paragraph.dateCreated !== $scope.paragraph.dateCreated ||
           data.paragraph.dateFinished !== $scope.paragraph.dateFinished ||
           data.paragraph.dateStarted !== $scope.paragraph.dateStarted ||
           data.paragraph.dateUpdated !== $scope.paragraph.dateUpdated ||
           data.paragraph.status !== $scope.paragraph.status ||
           data.paragraph.jobName !== $scope.paragraph.jobName ||
           data.paragraph.title !== $scope.paragraph.title ||
           isEmpty(data.paragraph.results) !== isEmpty($scope.paragraph.results) ||
           data.paragraph.errorMessage !== $scope.paragraph.errorMessage ||
           !angular.equals(data.paragraph.settings, $scope.paragraph.settings) ||
           !angular.equals(data.paragraph.config, $scope.paragraph.config))
         ) {
        var statusChanged = (data.paragraph.status !== $scope.paragraph.status);
        var resultRefreshed = (data.paragraph.dateFinished !== $scope.paragraph.dateFinished) ||
            isEmpty(data.paragraph.results) !== isEmpty($scope.paragraph.results) ||
          data.paragraph.status === 'ERROR' || (data.paragraph.status === 'FINISHED' && statusChanged);

        if ($scope.paragraph.text !== data.paragraph.text) {
          if ($scope.dirtyText) {         // check if editor has local update
            if ($scope.dirtyText === data.paragraph.text) {  // when local update is the same from remote, clear local update
              $scope.paragraph.text = data.paragraph.text;
              $scope.dirtyText = undefined;
              $scope.originalText = angular.copy(data.paragraph.text);
            } else { // if there're local update, keep it.
              $scope.paragraph.text = $scope.dirtyText;
            }
          } else {
            $scope.paragraph.text = data.paragraph.text;
            $scope.originalText = angular.copy(data.paragraph.text);
          }
        }

        /** broadcast update to result controller **/
        if (data.paragraph.results && data.paragraph.results.msg) {
          for (var i in data.paragraph.results.msg) {
            var newResult = data.paragraph.results.msg ? data.paragraph.results.msg[i] : {};
            var oldResult = ($scope.paragraph.results && $scope.paragraph.results.msg) ?
                $scope.paragraph.results.msg[i] : {};
            var newConfig = data.paragraph.config.results ? data.paragraph.config.results[i] : {};
            var oldConfig = $scope.paragraph.config.results ? $scope.paragraph.config.results[i] : {};
            if (!angular.equals(newResult, oldResult) ||
                !angular.equals(newConfig, oldConfig)) {
              $rootScope.$broadcast('updateResult', newResult, newConfig, data.paragraph, parseInt(i));
            }
          }
        }

        // resize col width
        if ($scope.paragraph.config.colWidth !== data.paragraph.colWidth) {
          $rootScope.$broadcast('paragraphResized', $scope.paragraph.id);
        }

        /** push the rest */
        $scope.paragraph.aborted = data.paragraph.aborted;
        $scope.paragraph.user = data.paragraph.user;
        $scope.paragraph.dateUpdated = data.paragraph.dateUpdated;
        $scope.paragraph.dateCreated = data.paragraph.dateCreated;
        $scope.paragraph.dateFinished = data.paragraph.dateFinished;
        $scope.paragraph.dateStarted = data.paragraph.dateStarted;
        $scope.paragraph.errorMessage = data.paragraph.errorMessage;
        $scope.paragraph.jobName = data.paragraph.jobName;
        $scope.paragraph.title = data.paragraph.title;
        $scope.paragraph.lineNumbers = data.paragraph.lineNumbers;
        $scope.paragraph.status = data.paragraph.status;
        $scope.paragraph.results = data.paragraph.results;
        $scope.paragraph.settings = data.paragraph.settings;
        $scope.editor.setReadOnly($scope.isRunning(data.paragraph));

        if (!$scope.asIframe) {
          $scope.paragraph.config = data.paragraph.config;
          initializeDefault(data.paragraph.config);
        } else {
          data.paragraph.config.editorHide = true;
          data.paragraph.config.tableHide = false;
          $scope.paragraph.config = data.paragraph.config;
        }

        if (statusChanged || resultRefreshed) {
          // when last paragraph runs, zeppelin automatically appends new paragraph.
          // this broadcast will focus to the newly inserted paragraph
          var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
          if (paragraphs.length >= 2 && paragraphs[paragraphs.length - 2].id.indexOf($scope.paragraph.id) === 0) {
            // rendering output can took some time. So delay scrolling event firing for sometime.
            setTimeout(function() {
              $rootScope.$broadcast('scrollToCursor');
            }, 500);
          }
        }
      }
    });

    $scope.$on('updateProgress', function(event, data) {
      if (data.id === $scope.paragraph.id) {
        $scope.currentProgress = data.progress;
      }
    });

    $scope.$on('keyEvent', function(event, keyEvent) {
      if ($scope.paragraphFocused) {

        var paragraphId = $scope.paragraph.id;
        var keyCode = keyEvent.keyCode;
        var noShortcutDefined = false;
        var editorHide = $scope.paragraph.config.editorHide;

        if (editorHide && (keyCode === 38 || (keyCode === 80 && keyEvent.ctrlKey && !keyEvent.altKey))) { // up
          // move focus to previous paragraph
          $scope.$emit('moveFocusToPreviousParagraph', paragraphId);
        } else if (editorHide && (keyCode === 40 || (keyCode === 78 && keyEvent.ctrlKey && !keyEvent.altKey))) { // down
          // move focus to next paragraph
          $scope.$emit('moveFocusToNextParagraph', paragraphId);
        } else if (keyEvent.shiftKey && keyCode === 13) { // Shift + Enter
          $scope.run($scope.paragraph, $scope.editor.getValue());
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 67) { // Ctrl + Alt + c
          $scope.cancelParagraph($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 68) { // Ctrl + Alt + d
          $scope.removeParagraph($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 75) { // Ctrl + Alt + k
          $scope.moveUp($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 74) { // Ctrl + Alt + j
          $scope.moveDown($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 65) { // Ctrl + Alt + a
          $scope.insertNew('above');
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 66) { // Ctrl + Alt + b
          $scope.insertNew('below');
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 79) { // Ctrl + Alt + o
          $scope.toggleOutput($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 82) { // Ctrl + Alt + r
          $scope.toggleEnableDisable($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 69) { // Ctrl + Alt + e
          $scope.toggleEditor($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 77) { // Ctrl + Alt + m
          if ($scope.paragraph.config.lineNumbers) {
            $scope.hideLineNumbers($scope.paragraph);
          } else {
            $scope.showLineNumbers($scope.paragraph);
          }
        } else if (keyEvent.ctrlKey && keyEvent.shiftKey && keyCode === 189) { // Ctrl + Shift + -
          $scope.changeColWidth($scope.paragraph, Math.max(1, $scope.paragraph.config.colWidth - 1));
        } else if (keyEvent.ctrlKey && keyEvent.shiftKey && keyCode === 187) { // Ctrl + Shift + =
          $scope.changeColWidth($scope.paragraph, Math.min(12, $scope.paragraph.config.colWidth + 1));
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 84) { // Ctrl + Alt + t
          if ($scope.paragraph.config.title) {
            $scope.hideTitle($scope.paragraph);
          } else {
            $scope.showTitle($scope.paragraph);
          }
        } else if (keyEvent.ctrlKey && keyEvent.shiftKey && keyCode === 67) { // Ctrl + Alt + c
          $scope.copyPara('below');
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 76) { // Ctrl + Alt + l
          $scope.clearParagraphOutput($scope.paragraph);
        } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 87) { // Ctrl + Alt + w
          $scope.goToSingleParagraph();
        } else {
          noShortcutDefined = true;
        }

        if (!noShortcutDefined) {
          keyEvent.preventDefault();
        }
      }
    });

    $scope.$on('focusParagraph', function(event, paragraphId, cursorPos, mouseEvent) {
      if ($scope.paragraph.id === paragraphId) {
        // focus editor
        if (!$scope.paragraph.config.editorHide) {
          if (!mouseEvent) {
            $scope.editor.focus();
            // move cursor to the first row (or the last row)
            var row;
            if (cursorPos >= 0) {
              row = cursorPos;
              $scope.editor.gotoLine(row, 0);
            } else {
              row = $scope.editor.session.getLength();
              $scope.editor.gotoLine(row, 0);
            }
            $scope.scrollToCursor($scope.paragraph.id, 0);
          }
        }
        $scope.handleFocus(true);
      } else {
        $scope.editor.blur();
        var isDigestPass = true;
        $scope.handleFocus(false, isDigestPass);
      }
    });

    $scope.$on('doubleClickParagraph', function(event, paragraphId) {
      if ($scope.paragraph.id === paragraphId && $scope.paragraph.config.editorHide &&
          $scope.paragraph.config.editorSetting.editOnDblClick) {
        var deferred = $q.defer();
        openEditorAndCloseTable($scope.paragraph);
        $timeout(
          $scope.$on('updateParagraph', function(event, data) {
            deferred.resolve(data);
          }
        ), 1000);

        deferred.promise.then(function(data) {
          $scope.editor.focus();
          $scope.goToEnd($scope.editor);
        });
      }
    });

    $scope.$on('openEditor', function(event) {
      $scope.openEditor($scope.paragraph);
    });

    $scope.$on('closeEditor', function(event) {
      $scope.closeEditor($scope.paragraph);
    });

    $scope.$on('openTable', function(event) {
      $scope.openTable($scope.paragraph);
    });

    $scope.$on('closeTable', function(event) {
      $scope.closeTable($scope.paragraph);
    });
  }
})();
