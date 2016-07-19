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

angular.module('zeppelinWebApp').controller('ParagraphCtrl', function($scope, $rootScope, $route, $window,
                                                                      $routeParams, $location, $timeout, $compile,
                                                                      $http, websocketMsgSrv, baseUrlSrv, ngToast,
                                                                      saveAsService) {
  var ANGULAR_FUNCTION_OBJECT_NAME_PREFIX = '_Z_ANGULAR_FUNC_';
  $scope.parentNote = null;
  $scope.paragraph = null;
  $scope.originalText = '';
  $scope.editor = null;

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

  var editorModes = {
    'ace/mode/python': /^%(\w*\.)?(pyspark|python)\s*$/,
    'ace/mode/scala': /^%(\w*\.)?spark\s*$/,
    'ace/mode/r': /^%(\w*\.)?(r|sparkr|knitr)\s*$/,
    'ace/mode/sql': /^%(\w*\.)?\wql/,
    'ace/mode/markdown': /^%md/,
    'ace/mode/sh': /^%sh/
  };

  // Controller init
  $scope.init = function(newParagraph, note) {
    $scope.paragraph = newParagraph;
    $scope.parentNote = note;
    $scope.originalText = angular.copy(newParagraph.text);
    $scope.chart = {};
    $scope.colWidthOption = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
    $scope.showTitleEditor = false;
    $scope.paragraphFocused = false;
    if (newParagraph.focus) {
      $scope.paragraphFocused = true;
    }

    if (!$scope.paragraph.config) {
      $scope.paragraph.config = {};
    }

    initializeDefault();

    if ($scope.getResultType() === 'TABLE') {
      $scope.loadTableData($scope.paragraph.result);
      $scope.setGraphMode($scope.getGraphMode(), false, false);
    } else if ($scope.getResultType() === 'HTML') {
      $scope.renderHtml();
    } else if ($scope.getResultType() === 'ANGULAR') {
      $scope.renderAngular();
    } else if ($scope.getResultType() === 'TEXT') {
      $scope.renderText();
    }

    getApplicationStates();
    getSuggestions();

    var activeApp =  _.get($scope.paragraph.config, 'helium.activeApp');
    if (activeApp) {
      var app = _.find($scope.apps, {id: activeApp});
      renderApp(app);
    }
  };

  $scope.renderHtml = function() {
    var retryRenderer = function() {
      if (angular.element('#p' + $scope.paragraph.id + '_html').length) {
        try {
          angular.element('#p' + $scope.paragraph.id + '_html').html($scope.paragraph.result.msg);

          angular.element('#p' + $scope.paragraph.id + '_html').find('pre code').each(function(i, e) {
            hljs.highlightBlock(e);
          });
        } catch (err) {
          console.log('HTML rendering error %o', err);
        }
      } else {
        $timeout(retryRenderer, 10);
      }
    };
    $timeout(retryRenderer);
  };

  $scope.renderAngular = function() {
    var retryRenderer = function() {
      if (angular.element('#p' + $scope.paragraph.id + '_angular').length) {
        try {
          angular.element('#p' + $scope.paragraph.id + '_angular').html($scope.paragraph.result.msg);

          $compile(angular.element('#p' + $scope.paragraph.id + '_angular').contents())(paragraphScope);
        } catch (err) {
          console.log('ANGULAR rendering error %o', err);
        }
      } else {
        $timeout(retryRenderer, 10);
      }
    };
    $timeout(retryRenderer);
  };

  $scope.renderText = function() {
    var retryRenderer = function() {

      var textEl = angular.element('#p' + $scope.paragraph.id + '_text');
      if (textEl.length) {
        // clear all lines before render
        $scope.clearTextOutput();

        if ($scope.paragraph.result && $scope.paragraph.result.msg) {
          $scope.appendTextOutput($scope.paragraph.result.msg);
        }

        angular.element('#p' + $scope.paragraph.id + '_text').bind('mousewheel', function(e) {
          $scope.keepScrollDown = false;
        });
        $scope.flushStreamingOutput = true;
      } else {
        $timeout(retryRenderer, 10);
      }
    };
    $timeout(retryRenderer);
  };

  $scope.clearTextOutput = function() {
    var textEl = angular.element('#p' + $scope.paragraph.id + '_text');
    if (textEl.length) {
      textEl.children().remove();
    }
  };

  $scope.appendTextOutput = function(msg) {
    var textEl = angular.element('#p' + $scope.paragraph.id + '_text');
    if (textEl.length) {
      var lines = msg.split('\n');
      for (var i = 0; i < lines.length; i++) {
        textEl.append(angular.element('<div></div>').text(lines[i]));
      }
    }
    if ($scope.keepScrollDown) {
      var doc = angular.element('#p' + $scope.paragraph.id + '_text');
      doc[0].scrollTop = doc[0].scrollHeight;
    }
  };

  var initializeDefault = function() {
    var config = $scope.paragraph.config;

    if (!config.colWidth) {
      config.colWidth = 12;
    }

    if (!config.graph) {
      config.graph = {};
    }

    if (!config.graph.mode) {
      config.graph.mode = 'table';
    }

    if (!config.graph.height) {
      config.graph.height = 300;
    }

    if (!config.graph.optionOpen) {
      config.graph.optionOpen = false;
    }

    if (!config.graph.keys) {
      config.graph.keys = [];
    }

    if (!config.graph.values) {
      config.graph.values = [];
    }

    if (!config.graph.groups) {
      config.graph.groups = [];
    }

    if (!config.graph.scatter) {
      config.graph.scatter = {};
    }

    if (config.enabled === undefined) {
      config.enabled = true;
    }
  };

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

  $scope.isRunning = function() {
    if ($scope.paragraph.status === 'RUNNING' || $scope.paragraph.status === 'PENDING') {
      return true;
    } else {
      return false;
    }
  };

  $scope.cancelParagraph = function() {
    console.log('Cancel %o', $scope.paragraph.id);
    websocketMsgSrv.cancelParagraphRun($scope.paragraph.id);
  };

  $scope.runParagraph = function(data) {
    websocketMsgSrv.runParagraph($scope.paragraph.id, $scope.paragraph.title,
                                 data, $scope.paragraph.config, $scope.paragraph.settings.params);
    $scope.originalText = angular.copy(data);
    $scope.dirtyText = undefined;
  };

  $scope.saveParagraph = function() {
    if ($scope.dirtyText === undefined || $scope.dirtyText === $scope.originalText) {
      return;
    }
    commitParagraph($scope.paragraph.title, $scope.dirtyText, $scope.paragraph.config,
      $scope.paragraph.settings.params);
    $scope.originalText = angular.copy($scope.dirtyText);
    $scope.dirtyText = undefined;
  };

  $scope.toggleEnableDisable = function() {
    $scope.paragraph.config.enabled = $scope.paragraph.config.enabled ? false : true;
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.run = function() {
    var editorValue = $scope.editor.getValue();
    if (editorValue) {
      if (!($scope.paragraph.status === 'RUNNING' || $scope.paragraph.status === 'PENDING')) {
        $scope.runParagraph(editorValue);
      }
    }
  };

  $scope.moveUp = function() {
    $scope.$emit('moveParagraphUp', $scope.paragraph.id);
  };

  $scope.moveDown = function() {
    $scope.$emit('moveParagraphDown', $scope.paragraph.id);
  };

  $scope.insertNew = function(position) {
    $scope.$emit('insertParagraph', $scope.paragraph.id, position || 'below');
  };

  $scope.removeParagraph = function() {
    var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
    if (paragraphs[paragraphs.length - 1].id.startsWith($scope.paragraph.id)) {
      BootstrapDialog.alert({
        closable: true,
        message: 'The last paragraph can\'t be deleted.'
      });
    } else {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to delete this paragraph?',
        callback: function(result) {
          if (result) {
            console.log('Remove paragraph');
            websocketMsgSrv.removeParagraph($scope.paragraph.id);
          }
        }
      });
    }
  };

  $scope.clearParagraphOutput = function() {
    websocketMsgSrv.clearParagraphOutput($scope.paragraph.id);
  };

  $scope.toggleEditor = function() {
    if ($scope.paragraph.config.editorHide) {
      $scope.openEditor();
    } else {
      $scope.closeEditor();
    }
  };

  $scope.closeEditor = function() {
    console.log('close the note');

    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.editorHide = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.openEditor = function() {
    console.log('open the note');

    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.editorHide = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.closeTable = function() {
    console.log('close the output');

    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.tableHide = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.openTable = function() {
    console.log('open the output');

    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.tableHide = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.showTitle = function() {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.title = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.hideTitle = function() {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.title = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.setTitle = function() {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.showLineNumbers = function() {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.lineNumbers = true;
    $scope.editor.renderer.setShowGutter(true);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.hideLineNumbers = function() {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.lineNumbers = false;
    $scope.editor.renderer.setShowGutter(false);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.columnWidthClass = function(n) {
    if ($scope.asIframe) {
      return 'col-md-12';
    } else {
      return 'col-md-' + n;
    }
  };

  $scope.changeColWidth = function(width) {
    angular.element('.navbar-right.open').removeClass('open');
    if (!width || width !== $scope.paragraph.config.colWidth) {
      if (width) {
        $scope.paragraph.config.colWidth = width;
      }
      var newParams = angular.copy($scope.paragraph.settings.params);
      var newConfig = angular.copy($scope.paragraph.config);

      commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
    }
  };

  $scope.toggleGraphOption = function() {
    var newConfig = angular.copy($scope.paragraph.config);
    if (newConfig.graph.optionOpen) {
      newConfig.graph.optionOpen = false;
    } else {
      newConfig.graph.optionOpen = true;
    }
    var newParams = angular.copy($scope.paragraph.settings.params);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.toggleOutput = function() {
    var newConfig = angular.copy($scope.paragraph.config);
    newConfig.tableHide = !newConfig.tableHide;
    var newParams = angular.copy($scope.paragraph.settings.params);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.toggleLineWithFocus = function() {
    var mode = $scope.getGraphMode();

    if (mode === 'lineWithFocusChart') {
      $scope.setGraphMode('lineChart', true);
      return true;
    }

    if (mode === 'lineChart') {
      $scope.setGraphMode('lineWithFocusChart', true);
      return true;
    }

    return false;
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

  $scope.aceChanged = function() {
    $scope.dirtyText = $scope.editor.getSession().getValue();
    $scope.startSaveTimer();

    $timeout(function() {
      $scope.setParagraphMode($scope.editor.getSession(), $scope.dirtyText, $scope.editor.getCursorPosition());
    });
  };

  $scope.aceLoaded = function(_editor) {
    var langTools = ace.require('ace/ext/language_tools');
    var Range = ace.require('ace/range').Range;

    _editor.$blockScrolling = Infinity;
    $scope.editor = _editor;
    if (_editor.container.id !== '{{paragraph.id}}_editor') {
      $scope.editor.renderer.setShowGutter($scope.paragraph.config.lineNumbers);
      $scope.editor.setShowFoldWidgets(false);
      $scope.editor.setHighlightActiveLine(false);
      $scope.editor.setHighlightGutterLine(false);
      $scope.editor.getSession().setUseWrapMode(true);
      $scope.editor.setTheme('ace/theme/chrome');
      if ($scope.paragraphFocused) {
        $scope.editor.focus();
        $scope.goToLineEnd();
      }

      autoAdjustEditorHeight(_editor.container.id);
      angular.element(window).resize(function() {
        autoAdjustEditorHeight(_editor.container.id);
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

      $scope.setParagraphMode = function(session, paragraphText, pos) {
        // Evaluate the mode only if the first 30 characters of the paragraph have been modified or the the position is undefined.
        if ((typeof pos === 'undefined') || (pos.row === 0 && pos.column < 30)) {
          // If paragraph loading, use config value if exists
          if ((typeof pos === 'undefined') && $scope.paragraph.config.editorMode) {
            session.setMode($scope.paragraph.config.editorMode);
          } else {
            // Defaults to spark mode
            var newMode = 'ace/mode/scala';
            // Test first against current mode
            var oldMode = session.getMode().$id;
            if (!editorModes[oldMode] || !editorModes[oldMode].test(paragraphText)) {
              for (var key in editorModes) {
                if (key !== oldMode) {
                  if (editorModes[key].test(paragraphText)) {
                    $scope.paragraph.config.editorMode = key;
                    session.setMode(key);
                    return true;
                  }
                }
              }
              $scope.paragraph.config.editorMode = newMode;
              session.setMode(newMode);
            }
          }
        }
      };

      var remoteCompleter = {
        getCompletions: function(editor, session, pos, prefix, callback) {
          if (!$scope.editor.isFocused()) { return;}

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

      $scope.handleFocus = function(value) {
        $scope.paragraphFocused = value;
        // Protect against error in case digest is already running
        $timeout(function() {
          // Apply changes since they come from 3rd party library
          $scope.$digest();
        });
      };

      $scope.editor.on('focus', function() {
        $scope.handleFocus(true);
      });

      $scope.editor.on('blur', function() {
        $scope.handleFocus(false);
      });

      $scope.editor.getSession().on('change', function(e, editSession) {
        autoAdjustEditorHeight(_editor.container.id);
      });

      $scope.setParagraphMode($scope.editor.getSession(), $scope.editor.getSession().getValue());

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

      // autocomplete on 'ctrl+.'
      $scope.editor.commands.bindKey('ctrl-.', 'startAutocomplete');
      $scope.editor.commands.bindKey('ctrl-space', null);

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

          var numRows;
          var currentRow;

          if (keyCode === 38 || (keyCode === 80 && e.ctrlKey && !e.altKey)) {  // UP
            numRows = $scope.editor.getSession().getLength();
            currentRow = $scope.editor.getCursorPosition().row;
            if (currentRow === 0) {
              // move focus to previous paragraph
              $scope.$emit('moveFocusToPreviousParagraph', $scope.paragraph.id);
            } else {
              $scope.scrollToCursor($scope.paragraph.id, -1);
            }
          } else if (keyCode === 40 || (keyCode === 78 && e.ctrlKey && !e.altKey)) {  // DOWN
            numRows = $scope.editor.getSession().getLength();
            currentRow = $scope.editor.getCursorPosition().row;
            if (currentRow === numRows - 1) {
              // move focus to next paragraph
              $scope.$emit('moveFocusToNextParagraph', $scope.paragraph.id);
            } else {
              $scope.scrollToCursor($scope.paragraph.id, 1);
            }
          }
        }
        this.origOnCommandKey(e, hashId, keyCode);
      };
    }
  };

  var autoAdjustEditorHeight = function(id) {
    var editor = $scope.editor;
    var height = editor.getSession().getScreenLength() * editor.renderer.lineHeight +
      editor.renderer.scrollBar.getWidth();

    angular.element('#' + id).height(height.toString() + 'px');
    editor.resize();
  };

  $rootScope.$on('scrollToCursor', function(event) {
    // scroll on 'scrollToCursor' event only when cursor is in the last paragraph
    var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
    if (paragraphs[paragraphs.length - 1].id.startsWith($scope.paragraph.id)) {
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
    return ($scope.currentProgress) ? $scope.currentProgress : 0;
  };

  $scope.getExecutionTime = function() {
    var pdata = $scope.paragraph;
    var timeMs = Date.parse(pdata.dateFinished) - Date.parse(pdata.dateStarted);
    if (isNaN(timeMs) || timeMs < 0) {
      if ($scope.isResultOutdated()) {
        return 'outdated';
      }
      return '';
    }
    var user = (pdata.user === undefined || pdata.user === null) ? 'anonymous' : pdata.user;
    var desc = 'Took ' + moment.duration((timeMs / 1000), 'seconds').format('h [hrs] m [min] s [sec]') +
      '. Last updated by ' + user + ' at ' + moment(pdata.dateUpdated).format('MMMM DD YYYY, h:mm:ss A') + '.';
    if ($scope.isResultOutdated()) {
      desc += ' (outdated)';
    }
    return desc;
  };

  $scope.getElapsedTime = function() {
    return 'Started ' + moment($scope.paragraph.dateStarted).fromNow() + '.';
  };

  $scope.isResultOutdated = function() {
    var pdata = $scope.paragraph;
    if (pdata.dateUpdated !== undefined && Date.parse(pdata.dateUpdated) > Date.parse(pdata.dateStarted)) {
      return true;
    }
    return false;
  };

  $scope.goToLineEnd = function() {
    $scope.editor.navigateLineEnd();
  };

  $scope.getResultType = function(paragraph) {
    var pdata = (paragraph) ? paragraph : $scope.paragraph;
    if (pdata.result && pdata.result.type) {
      return pdata.result.type;
    } else {
      return 'TEXT';
    }
  };

  $scope.getBase64ImageSrc = function(base64Data) {
    return 'data:image/png;base64,' + base64Data;
  };

  $scope.getGraphMode = function(paragraph) {
    var pdata = (paragraph) ? paragraph : $scope.paragraph;
    if (pdata.config.graph && pdata.config.graph.mode) {
      return pdata.config.graph.mode;
    } else {
      return 'table';
    }
  };

  $scope.loadTableData = function(result) {
    if (!result) {
      return;
    }
    if (result.type === 'TABLE') {
      var columnNames = [];
      var rows = [];
      var array = [];
      var textRows = result.msg.split('\n');
      result.comment = '';
      var comment = false;

      for (var i = 0; i < textRows.length; i++) {
        var textRow = textRows[i];
        if (comment) {
          result.comment += textRow;
          continue;
        }

        if (textRow === '') {
          if (rows.length > 0) {
            comment = true;
          }
          continue;
        }
        var textCols = textRow.split('\t');
        var cols = [];
        var cols2 = [];
        for (var j = 0; j < textCols.length; j++) {
          var col = textCols[j];
          if (i === 0) {
            columnNames.push({name: col, index: j, aggr: 'sum'});
          } else {
            cols.push(col);
            cols2.push({key: (columnNames[i]) ? columnNames[i].name : undefined, value: col});
          }
        }
        if (i !== 0) {
          rows.push(cols);
          array.push(cols2);
        }
      }
      result.msgTable = array;
      result.columnNames = columnNames;
      result.rows = rows;
    }
  };

  $scope.setGraphMode = function(type, emit, refresh) {
    if (emit) {
      setNewMode(type);
    } else {
      clearUnknownColsFromGraphOption();
      // set graph height
      var height = $scope.paragraph.config.graph.height;
      angular.element('#p' + $scope.paragraph.id + '_graph').height(height);

      if (!type || type === 'table') {
        setTable($scope.paragraph.result, refresh);
      } else {
        setD3Chart(type, $scope.paragraph.result, refresh);
      }
    }
  };

  var setNewMode = function(newMode) {
    var newConfig = angular.copy($scope.paragraph.config);
    var newParams = angular.copy($scope.paragraph.settings.params);

    // graph options
    newConfig.graph.mode = newMode;

    // see switchApp()
    _.set(newConfig, 'helium.activeApp', undefined);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  var commitParagraph = function(title, text, config, params) {
    websocketMsgSrv.commitParagraph($scope.paragraph.id, title, text, config, params);
  };

  var setTable = function(data, refresh) {
    var renderTable = function() {
      var height = $scope.paragraph.config.graph.height;
      var container = angular.element('#p' + $scope.paragraph.id + '_table').css('height', height).get(0);
      var resultRows = data.rows;
      var columnNames = _.pluck(data.columnNames, 'name');

      if ($scope.hot) {
        $scope.hot.destroy();
      }

      $scope.hot = new Handsontable(container, {
        colHeaders: columnNames,
        data: resultRows,
        rowHeaders: false,
        stretchH: 'all',
        sortIndicator: true,
        columnSorting: true,
        contextMenu: false,
        manualColumnResize: true,
        manualRowResize: true,
        readOnly: true,
        readOnlyCellClassName: '',  // don't apply any special class so we can retain current styling
        fillHandle: false,
        fragmentSelection: true,
        disableVisualSelection: true,
        cells: function(row, col, prop) {
          var cellProperties = {};
          cellProperties.renderer = function(instance, td, row, col, prop, value, cellProperties) {
            if (!isNaN(value)) {
              cellProperties.format = '0,0.[00000]';
              td.style.textAlign = 'left';
              Handsontable.renderers.NumericRenderer.apply(this, arguments);
            } else if (value.length > '%html'.length && '%html ' === value.substring(0, '%html '.length)) {
              td.innerHTML = value.substring('%html'.length);
            } else {
              Handsontable.renderers.TextRenderer.apply(this, arguments);
            }
          };
          return cellProperties;
        }
      });
    };

    var retryRenderer = function() {
      if (angular.element('#p' + $scope.paragraph.id + '_table').length) {
        try {
          renderTable();
        } catch (err) {
          console.log('Chart drawing error %o', err);
        }
      } else {
        $timeout(retryRenderer,10);
      }
    };
    $timeout(retryRenderer);

  };

  var groupedThousandsWith3DigitsFormatter = function(x) {
    return d3.format(',')(d3.round(x, 3));
  };

  var customAbbrevFormatter = function(x) {
    var s = d3.format('.3s')(x);
    switch (s[s.length - 1]) {
      case 'G': return s.slice(0, -1) + 'B';
    }
    return s;
  };

  var xAxisTickFormat = function(d, xLabels) {
    if (xLabels[d] && (isNaN(parseFloat(xLabels[d])) || !isFinite(xLabels[d]))) { // to handle string type xlabel
      return xLabels[d];
    } else {
      return d;
    }
  };

  var yAxisTickFormat = function(d) {
    if (d >= Math.pow(10,6)) {
      return customAbbrevFormatter(d);
    }
    return groupedThousandsWith3DigitsFormatter(d);
  };

  var setD3Chart = function(type, data, refresh) {
    if (!$scope.chart[type]) {
      var chart = nv.models[type]();
      $scope.chart[type] = chart;
    }

    var d3g = [];
    var xLabels;
    var yLabels;

    if (type === 'scatterChart') {
      var scatterData = setScatterChart(data, refresh);

      xLabels = scatterData.xLabels;
      yLabels = scatterData.yLabels;
      d3g = scatterData.d3g;

      $scope.chart[type].xAxis.tickFormat(function(d) {return xAxisTickFormat(d, xLabels);});
      $scope.chart[type].yAxis.tickFormat(function(d) {return xAxisTickFormat(d, yLabels);});

      // configure how the tooltip looks.
      $scope.chart[type].tooltipContent(function(key, x, y, graph, data) {
        var tooltipContent = '<h3>' + key + '</h3>';
        if ($scope.paragraph.config.graph.scatter.size &&
            $scope.isValidSizeOption($scope.paragraph.config.graph.scatter, $scope.paragraph.result.rows)) {
          tooltipContent += '<p>' + data.point.size + '</p>';
        }

        return tooltipContent;
      });

      $scope.chart[type].showDistX(true)
        .showDistY(true);
      //handle the problem of tooltip not showing when muliple points have same value.
    } else {
      var p = pivot(data);
      if (type === 'pieChart') {
        var d = pivotDataToD3ChartFormat(p, true).d3g;

        $scope.chart[type].x(function(d) { return d.label;})
          .y(function(d) { return d.value;});

        if (d.length > 0) {
          for (var i = 0; i < d[0].values.length ; i++) {
            var e = d[0].values[i];
            d3g.push({
              label: e.x,
              value: e.y
            });
          }
        }
      } else if (type === 'multiBarChart') {
        d3g = pivotDataToD3ChartFormat(p, true, false, type).d3g;
        $scope.chart[type].yAxis.axisLabelDistance(50);
        $scope.chart[type].yAxis.tickFormat(function(d) {return yAxisTickFormat(d);});
      } else if (type === 'lineChart' || type === 'stackedAreaChart' || type === 'lineWithFocusChart') {
        var pivotdata = pivotDataToD3ChartFormat(p, false, true);
        xLabels = pivotdata.xLabels;
        d3g = pivotdata.d3g;
        $scope.chart[type].xAxis.tickFormat(function(d) {return xAxisTickFormat(d, xLabels);});
        $scope.chart[type].yAxis.tickFormat(function(d) {return yAxisTickFormat(d);});
        $scope.chart[type].yAxis.axisLabelDistance(50);
        if ($scope.chart[type].useInteractiveGuideline) { // lineWithFocusChart hasn't got useInteractiveGuideline
          $scope.chart[type].useInteractiveGuideline(true); // for better UX and performance issue. (https://github.com/novus/nvd3/issues/691)
        }
        if ($scope.paragraph.config.graph.forceY) {
          $scope.chart[type].forceY([0]); // force y-axis minimum to 0 for line chart.
        } else {
          $scope.chart[type].forceY([]);
        }
      }
    }

    var renderChart = function() {
      if (!refresh) {
        // TODO force destroy previous chart
      }

      var height = $scope.paragraph.config.graph.height;

      var animationDuration = 300;
      var numberOfDataThreshold = 150;
      // turn off animation when dataset is too large. (for performance issue)
      // still, since dataset is large, the chart content sequentially appears like animated.
      try {
        if (d3g[0].values.length > numberOfDataThreshold) {
          animationDuration = 0;
        }
      } catch (ignoreErr) {
      }

      d3.select('#p' + $scope.paragraph.id + '_' + type + ' svg')
        .attr('height', $scope.paragraph.config.graph.height)
        .datum(d3g)
        .transition()
        .duration(animationDuration)
        .call($scope.chart[type]);
      d3.select('#p' + $scope.paragraph.id + '_' + type + ' svg').style.height = height + 'px';
      nv.utils.windowResize($scope.chart[type].update);
    };

    var retryRenderer = function() {
      if (angular.element('#p' + $scope.paragraph.id + '_' + type + ' svg').length !== 0) {
        try {
          renderChart();
        } catch (err) {
          console.log('Chart drawing error %o', err);
        }
      } else {
        $timeout(retryRenderer,10);
      }
    };
    $timeout(retryRenderer);
  };

  $scope.isGraphMode = function(graphName) {
    var activeAppId = _.get($scope.paragraph.config, 'helium.activeApp');
    if ($scope.getResultType() === 'TABLE' && $scope.getGraphMode() === graphName && !activeAppId) {
      return true;
    } else {
      return false;
    }
  };

  $scope.onGraphOptionChange = function() {
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeGraphOptionKeys = function(idx) {
    $scope.paragraph.config.graph.keys.splice(idx, 1);
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeGraphOptionValues = function(idx) {
    $scope.paragraph.config.graph.values.splice(idx, 1);
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeGraphOptionGroups = function(idx) {
    $scope.paragraph.config.graph.groups.splice(idx, 1);
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.setGraphOptionValueAggr = function(idx, aggr) {
    $scope.paragraph.config.graph.values[idx].aggr = aggr;
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeScatterOptionXaxis = function(idx) {
    $scope.paragraph.config.graph.scatter.xAxis = null;
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeScatterOptionYaxis = function(idx) {
    $scope.paragraph.config.graph.scatter.yAxis = null;
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeScatterOptionGroup = function(idx) {
    $scope.paragraph.config.graph.scatter.group = null;
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  $scope.removeScatterOptionSize = function(idx) {
    $scope.paragraph.config.graph.scatter.size = null;
    clearUnknownColsFromGraphOption();
    $scope.setGraphMode($scope.paragraph.config.graph.mode, true, false);
  };

  /* Clear unknown columns from graph option */
  var clearUnknownColsFromGraphOption = function() {
    var unique = function(list) {
      for (var i = 0; i < list.length; i++) {
        for (var j = i + 1; j < list.length; j++) {
          if (angular.equals(list[i], list[j])) {
            list.splice(j, 1);
          }
        }
      }
    };

    var removeUnknown = function(list) {
      for (var i = 0; i < list.length; i++) {
        // remove non existing column
        var found = false;
        for (var j = 0; j < $scope.paragraph.result.columnNames.length; j++) {
          var a = list[i];
          var b = $scope.paragraph.result.columnNames[j];
          if (a.index === b.index && a.name === b.name) {
            found = true;
            break;
          }
        }
        if (!found) {
          list.splice(i, 1);
        }
      }
    };

    var removeUnknownFromScatterSetting = function(fields) {
      for (var f in fields) {
        if (fields[f]) {
          var found = false;
          for (var i = 0; i < $scope.paragraph.result.columnNames.length; i++) {
            var a = fields[f];
            var b = $scope.paragraph.result.columnNames[i];
            if (a.index === b.index && a.name === b.name) {
              found = true;
              break;
            }
          }
          if (!found) {
            fields[f] = null;
          }
        }
      }
    };

    unique($scope.paragraph.config.graph.keys);
    removeUnknown($scope.paragraph.config.graph.keys);

    removeUnknown($scope.paragraph.config.graph.values);

    unique($scope.paragraph.config.graph.groups);
    removeUnknown($scope.paragraph.config.graph.groups);

    removeUnknownFromScatterSetting($scope.paragraph.config.graph.scatter);
  };

  /* select default key and value if there're none selected */
  var selectDefaultColsForGraphOption = function() {
    if ($scope.paragraph.config.graph.keys.length === 0 && $scope.paragraph.result.columnNames.length > 0) {
      $scope.paragraph.config.graph.keys.push($scope.paragraph.result.columnNames[0]);
    }

    if ($scope.paragraph.config.graph.values.length === 0 && $scope.paragraph.result.columnNames.length > 1) {
      $scope.paragraph.config.graph.values.push($scope.paragraph.result.columnNames[1]);
    }

    if (!$scope.paragraph.config.graph.scatter.xAxis && !$scope.paragraph.config.graph.scatter.yAxis) {
      if ($scope.paragraph.result.columnNames.length > 1) {
        $scope.paragraph.config.graph.scatter.xAxis = $scope.paragraph.result.columnNames[0];
        $scope.paragraph.config.graph.scatter.yAxis = $scope.paragraph.result.columnNames[1];
      } else if ($scope.paragraph.result.columnNames.length === 1) {
        $scope.paragraph.config.graph.scatter.xAxis = $scope.paragraph.result.columnNames[0];
      }
    }
  };

  var pivot = function(data) {
    var keys = $scope.paragraph.config.graph.keys;
    var groups = $scope.paragraph.config.graph.groups;
    var values = $scope.paragraph.config.graph.values;

    var aggrFunc = {
      sum: function(a, b) {
        var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
        var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
        return varA + varB;
      },
      count: function(a, b) {
        var varA = (a !== undefined) ? parseInt(a) : 0;
        var varB = (b !== undefined) ? 1 : 0;
        return varA + varB;
      },
      min: function(a, b) {
        var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
        var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
        return Math.min(varA,varB);
      },
      max: function(a, b) {
        var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
        var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
        return Math.max(varA,varB);
      },
      avg: function(a, b, c) {
        var varA = (a !== undefined) ? (isNaN(a) ? 1 : parseFloat(a)) : 0;
        var varB = (b !== undefined) ? (isNaN(b) ? 1 : parseFloat(b)) : 0;
        return varA + varB;
      }
    };

    var aggrFuncDiv = {
      sum: false,
      count: false,
      min: false,
      max: false,
      avg: true
    };

    var schema = {};
    var rows = {};

    for (var i = 0; i < data.rows.length; i++) {
      var row = data.rows[i];
      var s = schema;
      var p = rows;

      for (var k = 0; k < keys.length; k++) {
        var key = keys[k];

        // add key to schema
        if (!s[key.name]) {
          s[key.name] = {
            order: k,
            index: key.index,
            type: 'key',
            children: {}
          };
        }
        s = s[key.name].children;

        // add key to row
        var keyKey = row[key.index];
        if (!p[keyKey]) {
          p[keyKey] = {};
        }
        p = p[keyKey];
      }

      for (var g = 0; g < groups.length; g++) {
        var group = groups[g];
        var groupKey = row[group.index];

        // add group to schema
        if (!s[groupKey]) {
          s[groupKey] = {
            order: g,
            index: group.index,
            type: 'group',
            children: {}
          };
        }
        s = s[groupKey].children;

        // add key to row
        if (!p[groupKey]) {
          p[groupKey] = {};
        }
        p = p[groupKey];
      }

      for (var v = 0; v < values.length; v++) {
        var value = values[v];
        var valueKey = value.name + '(' + value.aggr + ')';

        // add value to schema
        if (!s[valueKey]) {
          s[valueKey] = {
            type: 'value',
            order: v,
            index: value.index
          };
        }

        // add value to row
        if (!p[valueKey]) {
          p[valueKey] = {
            value: (value.aggr !== 'count') ? row[value.index] : 1,
            count: 1
          };
        } else {
          p[valueKey] = {
            value: aggrFunc[value.aggr](p[valueKey].value, row[value.index], p[valueKey].count + 1),
            count: (aggrFuncDiv[value.aggr]) ?  p[valueKey].count + 1 : p[valueKey].count
          };
        }
      }
    }

    //console.log("schema=%o, rows=%o", schema, rows);

    return {
      schema: schema,
      rows: rows
    };
  };

  var pivotDataToD3ChartFormat = function(data, allowTextXAxis, fillMissingValues, chartType) {
    // construct d3 data
    var d3g = [];

    var schema = data.schema;
    var rows = data.rows;
    var values = $scope.paragraph.config.graph.values;

    var concat = function(o, n) {
      if (!o) {
        return n;
      } else {
        return o + '.' + n;
      }
    };

    var getSchemaUnderKey = function(key, s) {
      for (var c in key.children) {
        s[c] = {};
        getSchemaUnderKey(key.children[c], s[c]);
      }
    };

    var traverse = function(sKey, s, rKey, r, func, rowName, rowValue, colName) {
      //console.log("TRAVERSE sKey=%o, s=%o, rKey=%o, r=%o, rowName=%o, rowValue=%o, colName=%o", sKey, s, rKey, r, rowName, rowValue, colName);

      if (s.type === 'key') {
        rowName = concat(rowName, sKey);
        rowValue = concat(rowValue, rKey);
      } else if (s.type === 'group') {
        colName = concat(colName, rKey);
      } else if (s.type === 'value' && sKey === rKey || valueOnly) {
        colName = concat(colName, rKey);
        func(rowName, rowValue, colName, r);
      }

      for (var c in s.children) {
        if (fillMissingValues && s.children[c].type === 'group' && r[c] === undefined) {
          var cs = {};
          getSchemaUnderKey(s.children[c], cs);
          traverse(c, s.children[c], c, cs, func, rowName, rowValue, colName);
          continue;
        }

        for (var j in r) {
          if (s.children[c].type === 'key' || c === j) {
            traverse(c, s.children[c], j, r[j], func, rowName, rowValue, colName);
          }
        }
      }
    };

    var keys = $scope.paragraph.config.graph.keys;
    var groups = $scope.paragraph.config.graph.groups;
    values = $scope.paragraph.config.graph.values;
    var valueOnly = (keys.length === 0 && groups.length === 0 && values.length > 0);
    var noKey = (keys.length === 0);
    var isMultiBarChart = (chartType === 'multiBarChart');

    var sKey = Object.keys(schema)[0];

    var rowNameIndex = {};
    var rowIdx = 0;
    var colNameIndex = {};
    var colIdx = 0;
    var rowIndexValue = {};

    for (var k in rows) {
      traverse(sKey, schema[sKey], k, rows[k], function(rowName, rowValue, colName, value) {
        //console.log("RowName=%o, row=%o, col=%o, value=%o", rowName, rowValue, colName, value);
        if (rowNameIndex[rowValue] === undefined) {
          rowIndexValue[rowIdx] = rowValue;
          rowNameIndex[rowValue] = rowIdx++;
        }

        if (colNameIndex[colName] === undefined) {
          colNameIndex[colName] = colIdx++;
        }
        var i = colNameIndex[colName];
        if (noKey && isMultiBarChart) {
          i = 0;
        }

        if (!d3g[i]) {
          d3g[i] = {
            values: [],
            key: (noKey && isMultiBarChart) ? 'values' : colName
          };
        }

        var xVar = isNaN(rowValue) ? ((allowTextXAxis) ? rowValue : rowNameIndex[rowValue]) : parseFloat(rowValue);
        var yVar = 0;
        if (xVar === undefined) { xVar = colName; }
        if (value !== undefined) {
          yVar = isNaN(value.value) ? 0 : parseFloat(value.value) / parseFloat(value.count);
        }
        d3g[i].values.push({
          x: xVar,
          y: yVar
        });
      });
    }

    // clear aggregation name, if possible
    var namesWithoutAggr = {};
    var colName;
    var withoutAggr;
    // TODO - This part could use som refactoring - Weird if/else with similar actions and variable names
    for (colName in colNameIndex) {
      withoutAggr = colName.substring(0, colName.lastIndexOf('('));
      if (!namesWithoutAggr[withoutAggr]) {
        namesWithoutAggr[withoutAggr] = 1;
      } else {
        namesWithoutAggr[withoutAggr]++;
      }
    }

    if (valueOnly) {
      for (var valueIndex = 0; valueIndex < d3g[0].values.length; valueIndex++) {
        colName = d3g[0].values[valueIndex].x;
        if (!colName) {
          continue;
        }

        withoutAggr = colName.substring(0, colName.lastIndexOf('('));
        if (namesWithoutAggr[withoutAggr] <= 1) {
          d3g[0].values[valueIndex].x = withoutAggr;
        }
      }
    } else {
      for (var d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
        colName = d3g[d3gIndex].key;
        withoutAggr = colName.substring(0, colName.lastIndexOf('('));
        if (namesWithoutAggr[withoutAggr] <= 1) {
          d3g[d3gIndex].key = withoutAggr;
        }
      }

      // use group name instead of group.value as a column name, if there're only one group and one value selected.
      if (groups.length === 1 && values.length === 1) {
        for (d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
          colName = d3g[d3gIndex].key;
          colName = colName.split('.')[0];
          d3g[d3gIndex].key = colName;
        }
      }

    }

    return {
      xLabels: rowIndexValue,
      d3g: d3g
    };
  };

  var setDiscreteScatterData = function(data) {
    var xAxis = $scope.paragraph.config.graph.scatter.xAxis;
    var yAxis = $scope.paragraph.config.graph.scatter.yAxis;
    var group = $scope.paragraph.config.graph.scatter.group;

    var xValue;
    var yValue;
    var grp;

    var rows = {};

    for (var i = 0; i < data.rows.length; i++) {
      var row = data.rows[i];
      if (xAxis) {
        xValue = row[xAxis.index];
      }
      if (yAxis) {
        yValue = row[yAxis.index];
      }
      if (group) {
        grp = row[group.index];
      }

      var key = xValue + ',' + yValue +  ',' + grp;

      if (!rows[key]) {
        rows[key] = {
          x: xValue,
          y: yValue,
          group: grp,
          size: 1
        };
      } else {
        rows[key].size++;
      }
    }

    // change object into array
    var newRows = [];
    for (var r in rows) {
      var newRow = [];
      if (xAxis) { newRow[xAxis.index] = rows[r].x; }
      if (yAxis) { newRow[yAxis.index] = rows[r].y; }
      if (group) { newRow[group.index] = rows[r].group; }
      newRow[data.rows[0].length] = rows[r].size;
      newRows.push(newRow);
    }
    return newRows;
  };

  var setScatterChart = function(data, refresh) {
    var xAxis = $scope.paragraph.config.graph.scatter.xAxis;
    var yAxis = $scope.paragraph.config.graph.scatter.yAxis;
    var group = $scope.paragraph.config.graph.scatter.group;
    var size = $scope.paragraph.config.graph.scatter.size;

    var xValues = [];
    var yValues = [];
    var rows = {};
    var d3g = [];

    var rowNameIndex = {};
    var colNameIndex = {};
    var grpNameIndex = {};
    var rowIndexValue = {};
    var colIndexValue = {};
    var grpIndexValue = {};
    var rowIdx = 0;
    var colIdx = 0;
    var grpIdx = 0;
    var grpName = '';

    var xValue;
    var yValue;
    var row;

    if (!xAxis && !yAxis) {
      return {
        d3g: []
      };
    }

    for (var i = 0; i < data.rows.length; i++) {
      row = data.rows[i];
      if (xAxis) {
        xValue = row[xAxis.index];
        xValues[i] = xValue;
      }
      if (yAxis) {
        yValue = row[yAxis.index];
        yValues[i] = yValue;
      }
    }

    var isAllDiscrete = ((xAxis && yAxis && isDiscrete(xValues) && isDiscrete(yValues)) ||
                         (!xAxis && isDiscrete(yValues)) ||
                         (!yAxis && isDiscrete(xValues)));

    if (isAllDiscrete) {
      rows = setDiscreteScatterData(data);
    } else {
      rows = data.rows;
    }

    if (!group && isAllDiscrete) {
      grpName = 'count';
    } else if (!group && !size) {
      if (xAxis && yAxis) {
        grpName = '(' + xAxis.name + ', ' + yAxis.name + ')';
      } else if (xAxis && !yAxis) {
        grpName = xAxis.name;
      } else if (!xAxis && yAxis) {
        grpName = yAxis.name;
      }
    } else if (!group && size) {
      grpName = size.name;
    }

    for (i = 0; i < rows.length; i++) {
      row = rows[i];
      if (xAxis) {
        xValue = row[xAxis.index];
      }
      if (yAxis) {
        yValue = row[yAxis.index];
      }
      if (group) {
        grpName = row[group.index];
      }
      var sz = (isAllDiscrete) ? row[row.length - 1] : ((size) ? row[size.index] : 1);

      if (grpNameIndex[grpName] === undefined) {
        grpIndexValue[grpIdx] = grpName;
        grpNameIndex[grpName] = grpIdx++;
      }

      if (xAxis && rowNameIndex[xValue] === undefined) {
        rowIndexValue[rowIdx] = xValue;
        rowNameIndex[xValue] = rowIdx++;
      }

      if (yAxis && colNameIndex[yValue] === undefined) {
        colIndexValue[colIdx] = yValue;
        colNameIndex[yValue] = colIdx++;
      }

      if (!d3g[grpNameIndex[grpName]]) {
        d3g[grpNameIndex[grpName]] = {
          key: grpName,
          values: []
        };
      }

      d3g[grpNameIndex[grpName]].values.push({
        x: xAxis ? (isNaN(xValue) ? rowNameIndex[xValue] : parseFloat(xValue)) : 0,
        y: yAxis ? (isNaN(yValue) ? colNameIndex[yValue] : parseFloat(yValue)) : 0,
        size: isNaN(parseFloat(sz)) ? 1 : parseFloat(sz)
      });
    }

    return {
      xLabels: rowIndexValue,
      yLabels: colIndexValue,
      d3g: d3g
    };
  };

  var isDiscrete = function(field) {
    var getUnique = function(f) {
      var uniqObj = {};
      var uniqArr = [];
      var j = 0;
      for (var i = 0; i < f.length; i++) {
        var item = f[i];
        if (uniqObj[item] !== 1) {
          uniqObj[item] = 1;
          uniqArr[j++] = item;
        }
      }
      return uniqArr;
    };

    for (var i = 0; i < field.length; i++) {
      if (isNaN(parseFloat(field[i])) &&
         (typeof field[i] === 'string' || field[i] instanceof String)) {
        return true;
      }
    }

    var threshold = 0.05;
    var unique = getUnique(field);
    if (unique.length / field.length < threshold) {
      return true;
    } else {
      return false;
    }
  };

  $scope.isValidSizeOption = function(options, rows) {
    var xValues = [];
    var yValues = [];

    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var size = row[options.size.index];

      //check if the field is numeric
      if (isNaN(parseFloat(size)) || !isFinite(size)) {
        return false;
      }

      if (options.xAxis) {
        var x = row[options.xAxis.index];
        xValues[i] = x;
      }
      if (options.yAxis) {
        var y = row[options.yAxis.index];
        yValues[i] = y;
      }
    }

    //check if all existing fields are discrete
    var isAllDiscrete = ((options.xAxis && options.yAxis && isDiscrete(xValues) && isDiscrete(yValues)) ||
                         (!options.xAxis && isDiscrete(yValues)) ||
                         (!options.yAxis && isDiscrete(xValues)));

    if (isAllDiscrete) {
      return false;
    }

    return true;
  };

  $scope.resizeParagraph = function(width, height) {
    $scope.changeColWidth(width);
    $timeout(function() {
      autoAdjustEditorHeight($scope.paragraph.id + '_editor');
      $scope.changeHeight(height);
    }, 200);
  };

  $scope.changeHeight = function(height) {
    var newParams = angular.copy($scope.paragraph.settings.params);
    var newConfig = angular.copy($scope.paragraph.config);

    newConfig.graph.height = height;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  /** Utility function */
  if (typeof String.prototype.startsWith !== 'function') {
    String.prototype.startsWith = function(str) {
      return this.slice(0, str.length) === str;
    };
  }

  $scope.goToSingleParagraph = function() {
    var noteId = $route.current.pathParams.noteId;
    var redirectToUrl = location.protocol + '//' + location.host + location.pathname + '#/notebook/' + noteId +
      '/paragraph/' + $scope.paragraph.id + '?asIframe';
    $window.open(redirectToUrl);
  };

  $scope.showScrollDownIcon = function() {
    var doc = angular.element('#p' + $scope.paragraph.id + '_text');
    if (doc[0]) {
      return doc[0].scrollHeight > doc.innerHeight();
    }
    return false;
  };

  $scope.scrollParagraphDown = function() {
    var doc = angular.element('#p' + $scope.paragraph.id + '_text');
    doc.animate({scrollTop: doc[0].scrollHeight}, 500);
    $scope.keepScrollDown = true;
  };

  $scope.showScrollUpIcon = function() {
    if (angular.element('#p' + $scope.paragraph.id + '_text')[0]) {
      return angular.element('#p' + $scope.paragraph.id + '_text')[0].scrollTop !== 0;
    }
    return false;

  };

  $scope.scrollParagraphUp = function() {
    var doc = angular.element('#p' + $scope.paragraph.id + '_text');
    doc.animate({scrollTop: 0}, 500);
    $scope.keepScrollDown = false;
  };

  $scope.exportToDSV = function(delimiter) {
    var dsv = '';
    for (var titleIndex in $scope.paragraph.result.columnNames) {
      dsv += $scope.paragraph.result.columnNames[titleIndex].name + delimiter;
    }
    dsv = dsv.substring(0, dsv.length - 1) + '\n';
    for (var r in $scope.paragraph.result.msgTable) {
      var row = $scope.paragraph.result.msgTable[r];
      var dsvRow = '';
      for (var index in row) {
        dsvRow += row[index].value + delimiter;
      }
      dsv += dsvRow.substring(0, dsvRow.length - 1) + '\n';
    }
    var extension = '';
    if (delimiter === '\t') {
      extension = 'tsv';
    } else if (delimiter === ',') {
      extension = 'csv';
    }
    saveAsService.saveAs(dsv, 'data', extension);
  };

  // Helium ---------------------------------------------

  // app states
  $scope.apps = [];

  // suggested apps
  $scope.suggestion = {};

  $scope.switchApp = function(appId) {
    var config = $scope.paragraph.config;
    var settings = $scope.paragraph.settings;

    var newConfig = angular.copy(config);
    var newParams = angular.copy(settings.params);

    // 'helium.activeApp' can be cleared by setGraphMode()
    _.set(newConfig, 'helium.activeApp', appId);

    commitConfig(newConfig, newParams);
  };

  $scope.loadApp = function(heliumPackage) {
    var noteId = $route.current.pathParams.noteId;
    $http.post(baseUrlSrv.getRestApiBase() + '/helium/load/' + noteId + '/' + $scope.paragraph.id,
      heliumPackage)
      .success(function(data, status, headers, config) {
        console.log('Load app %o', data);
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
      });
  };

  var commitConfig = function(config, params) {
    var paragraph = $scope.paragraph;
    commitParagraph(paragraph.title, paragraph.text, config, params);
  };

  var getApplicationStates = function() {
    var appStates = [];
    var paragraph = $scope.paragraph;

    // Display ApplicationState
    if (paragraph.apps) {
      _.forEach(paragraph.apps, function(app) {
        appStates.push({
          id: app.id,
          pkg: app.pkg,
          status: app.status,
          output: app.output
        });
      });
    }

    // update or remove app states no longer exists
    _.forEach($scope.apps, function(currentAppState, idx) {
      var newAppState = _.find(appStates, {id: currentAppState.id});
      if (newAppState) {
        angular.extend($scope.apps[idx], newAppState);
      } else {
        $scope.apps.splice(idx, 1);
      }
    });

    // add new app states
    _.forEach(appStates, function(app, idx) {
      if ($scope.apps.length <= idx || $scope.apps[idx].id !== app.id) {
        $scope.apps.splice(idx, 0, app);
      }
    });
  };

  var getSuggestions = function() {
    // Get suggested apps
    var noteId = $route.current.pathParams.noteId;
    $http.get(baseUrlSrv.getRestApiBase() + '/helium/suggest/' + noteId + '/' + $scope.paragraph.id)
      .success(function(data, status, headers, config) {
        console.log('Suggested apps %o', data);
        $scope.suggestion = data.body;
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
      });
  };

  var getAppScope = function(appState) {
    if (!appState.scope) {
      appState.scope = $rootScope.$new(true, $rootScope);
    }

    return appState.scope;
  };

  var getAppRegistry = function(appState) {
    if (!appState.registry) {
      appState.registry = {};
    }

    return appState.registry;
  };

  var renderApp = function(appState) {
    var retryRenderer = function() {
      var targetEl = angular.element(document.getElementById('p' + appState.id));
      console.log('retry renderApp %o', targetEl);
      if (targetEl.length) {
        try {
          console.log('renderApp %o', appState);
          targetEl.html(appState.output);
          $compile(targetEl.contents())(getAppScope(appState));
        } catch (err) {
          console.log('App rendering error %o', err);
        }
      } else {
        $timeout(retryRenderer, 1000);
      }
    };
    $timeout(retryRenderer);
  };

  /*
  ** $scope.$on functions below
  */

  $scope.$on('appendAppOutput', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      var app = _.find($scope.apps, {id: data.appId});
      if (app) {
        app.output += data.data;

        var paragraphAppState = _.find($scope.paragraph.apps, {id: data.appId});
        paragraphAppState.output = app.output;

        var targetEl = angular.element(document.getElementById('p' + app.id));
        targetEl.html(app.output);
        $compile(targetEl.contents())(getAppScope(app));
        console.log('append app output %o', $scope.apps);
      }
    }
  });

  $scope.$on('updateAppOutput', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      var app = _.find($scope.apps, {id: data.appId});
      if (app) {
        app.output = data.data;

        var paragraphAppState = _.find($scope.paragraph.apps, {id: data.appId});
        paragraphAppState.output = app.output;

        var targetEl = angular.element(document.getElementById('p' + app.id));
        targetEl.html(app.output);
        $compile(targetEl.contents())(getAppScope(app));
        console.log('append app output');
      }
    }
  });

  $scope.$on('appLoad', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      var app = _.find($scope.apps, {id: data.appId});
      if (!app) {
        app = {
          id: data.appId,
          pkg: data.pkg,
          status: 'UNLOADED',
          output: ''
        };

        $scope.apps.push(app);
        $scope.paragraph.apps.push(app);
        $scope.switchApp(app.id);
      }
    }
  });

  $scope.$on('appStatusChange', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      var app = _.find($scope.apps, {id: data.appId});
      if (app) {
        app.status = data.status;
        var paragraphAppState = _.find($scope.paragraph.apps, {id: data.appId});
        paragraphAppState.status = app.status;
      }
    }
  });

  $scope.$on('angularObjectUpdate', function(event, data) {
    var noteId = $route.current.pathParams.noteId;
    if (!data.noteId || data.noteId === noteId) {
      var scope;
      var registry;

      if (!data.paragraphId || data.paragraphId === $scope.paragraph.id) {
        scope = paragraphScope;
        registry = angularObjectRegistry;
      } else {
        var app = _.find($scope.apps, {id: data.paragraphId});
        if (app) {
          scope = getAppScope(app);
          registry = getAppRegistry(app);
        } else {
          // no matching app in this paragraph
          return;
        }
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
      if (varName.startsWith(ANGULAR_FUNCTION_OBJECT_NAME_PREFIX)) {
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
        var app = _.find($scope.apps, {id: data.paragraphId});
        if (app) {
          scope = getAppScope(app);
          registry = getAppRegistry(app);
        } else {
          // no matching app in this paragraph
          return;
        }
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
      if (varName.startsWith(ANGULAR_FUNCTION_OBJECT_NAME_PREFIX)) {
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
         isEmpty(data.paragraph.result) !== isEmpty($scope.paragraph.result) ||
         data.paragraph.errorMessage !== $scope.paragraph.errorMessage ||
         !angular.equals(data.paragraph.settings, $scope.paragraph.settings) ||
         !angular.equals(data.paragraph.config, $scope.paragraph.config))
       ) {

      var oldType = $scope.getResultType();
      var newType = $scope.getResultType(data.paragraph);
      var oldGraphMode = $scope.getGraphMode();
      var newGraphMode = $scope.getGraphMode(data.paragraph);
      var oldActiveApp = _.get($scope.paragraph.config, 'helium.activeApp');
      var newActiveApp = _.get(data.paragraph.config, 'helium.activeApp');

      var statusChanged = (data.paragraph.status !== $scope.paragraph.status);

      var resultRefreshed = (data.paragraph.dateFinished !== $scope.paragraph.dateFinished) ||
        isEmpty(data.paragraph.result) !== isEmpty($scope.paragraph.result) ||
        data.paragraph.status === 'ERROR' || (data.paragraph.status === 'FINISHED' && statusChanged) ||
        (!newActiveApp && oldActiveApp !== newActiveApp);

      //console.log("updateParagraph oldData %o, newData %o. type %o -> %o, mode %o -> %o", $scope.paragraph, data, oldType, newType, oldGraphMode, newGraphMode);

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
      $scope.paragraph.result = data.paragraph.result;
      $scope.paragraph.settings = data.paragraph.settings;

      if (!$scope.asIframe) {
        $scope.paragraph.config = data.paragraph.config;
        initializeDefault();
      } else {
        data.paragraph.config.editorHide = true;
        data.paragraph.config.tableHide = false;
        $scope.paragraph.config = data.paragraph.config;
      }

      if (newType === 'TABLE') {
        $scope.loadTableData($scope.paragraph.result);
        if (oldType !== 'TABLE' || resultRefreshed) {
          clearUnknownColsFromGraphOption();
          selectDefaultColsForGraphOption();
        }
        /** User changed the chart type? */
        if (oldGraphMode !== newGraphMode) {
          $scope.setGraphMode(newGraphMode, false, false);
        } else {
          $scope.setGraphMode(newGraphMode, false, true);
        }
      } else if (newType === 'HTML' && resultRefreshed) {
        $scope.renderHtml();
      } else if (newType === 'ANGULAR' && resultRefreshed) {
        $scope.renderAngular();
      } else if (newType === 'TEXT' && resultRefreshed) {
        $scope.renderText();
      }

      getApplicationStates();
      getSuggestions();

      if (newActiveApp && newActiveApp !== oldActiveApp) {
        var app = _.find($scope.apps, {id: newActiveApp});
        renderApp(app);
      }

      if (statusChanged || resultRefreshed) {
        // when last paragraph runs, zeppelin automatically appends new paragraph.
        // this broadcast will focus to the newly inserted paragraph
        var paragraphs = angular.element('div[id$="_paragraphColumn_main"]');
        if (paragraphs.length >= 2 && paragraphs[paragraphs.length - 2].id.startsWith($scope.paragraph.id)) {
          // rendering output can took some time. So delay scrolling event firing for sometime.
          setTimeout(function() {
            $rootScope.$broadcast('scrollToCursor');
          }, 500);
        }
      }
    }

  });

  $scope.$on('appendParagraphOutput', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      if ($scope.flushStreamingOutput) {
        $scope.clearTextOutput();
        $scope.flushStreamingOutput = false;
      }
      $scope.appendTextOutput(data.data);
    }
  });

  $scope.$on('updateParagraphOutput', function(event, data) {
    if ($scope.paragraph.id === data.paragraphId) {
      $scope.clearTextOutput();
      $scope.appendTextOutput(data.data);
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
        $scope.run();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 67) { // Ctrl + Alt + c
        $scope.cancelParagraph();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 68) { // Ctrl + Alt + d
        $scope.removeParagraph();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 75) { // Ctrl + Alt + k
        $scope.moveUp();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 74) { // Ctrl + Alt + j
        $scope.moveDown();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 65) { // Ctrl + Alt + a
        $scope.insertNew('above');
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 66) { // Ctrl + Alt + b
        $scope.insertNew('below');
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 79) { // Ctrl + Alt + o
        $scope.toggleOutput();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 82) { // Ctrl + Alt + r
        $scope.toggleEnableDisable();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 69) { // Ctrl + Alt + e
        $scope.toggleEditor();
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 77) { // Ctrl + Alt + m
        if ($scope.paragraph.config.lineNumbers) {
          $scope.hideLineNumbers();
        } else {
          $scope.showLineNumbers();
        }
      } else if (keyEvent.ctrlKey && keyEvent.shiftKey && keyCode === 189) { // Ctrl + Shift + -
        $scope.changeColWidth(Math.max(1, $scope.paragraph.config.colWidth - 1));
      } else if (keyEvent.ctrlKey && keyEvent.shiftKey && keyCode === 187) { // Ctrl + Shift + =
        $scope.changeColWidth(Math.min(12, $scope.paragraph.config.colWidth + 1));
      } else if (keyEvent.ctrlKey && keyEvent.altKey && keyCode === 84) { // Ctrl + Alt + t
        if ($scope.paragraph.config.title) {
          $scope.hideTitle();
        } else {
          $scope.showTitle();
        }
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
      $scope.handleFocus(false);
    }
  });

  $scope.$on('runParagraph', function(event) {
    $scope.runParagraph($scope.editor.getValue());
  });

  $scope.$on('openEditor', function(event) {
    $scope.openEditor();
  });

  $scope.$on('closeEditor', function(event) {
    $scope.closeEditor();
  });

  $scope.$on('openTable', function(event) {
    $scope.openTable();
  });

  $scope.$on('closeTable', function(event) {
    $scope.closeTable();
  });

});
