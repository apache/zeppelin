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

  angular.module('zeppelinWebApp').controller('ResultCtrl', ResultCtrl);

  ResultCtrl.$inject = [
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
    '$templateRequest',
    'websocketMsgSrv',
    'baseUrlSrv',
    'ngToast',
    'saveAsService',
    'noteVarShareService'
  ];

  function ResultCtrl($scope, $rootScope, $route, $window, $routeParams, $location,
                      $timeout, $compile, $http, $q, $templateRequest, websocketMsgSrv,
                      baseUrlSrv, ngToast, saveAsService, noteVarShareService) {

    /**
     * Built-in visualizations
     */
    $scope.builtInTableDataVisualizationList = [
      {
        id: 'table',   // paragraph.config.graph.mode
        name: 'Table', // human readable name. tooltip
        icon: 'fa fa-table'
      },
      {
        id: 'multiBarChart',
        name: 'Bar Chart',
        icon: 'fa fa-bar-chart',
        transformation: 'pivot'
      },
      {
        id: 'pieChart',
        name: 'Pie Chart',
        icon: 'fa fa-pie-chart',
        transformation: 'pivot'
      },
      {
        id: 'stackedAreaChart',
        name: 'Area Chart',
        icon: 'fa fa-area-chart',
        transformation: 'pivot'
      },
      {
        id: 'lineChart',
        name: 'Line Chart',
        icon: 'fa fa-line-chart',
        transformation: 'pivot'
      },
      {
        id: 'scatterChart',
        name: 'Scatter Chart',
        icon: 'cf cf-scatter-chart'
      }
    ];

    /**
     * Holds class and actual runtime instance and related infos of built-in visualizations
     */
    var builtInVisualizations = {
      'table': {
        class: zeppelin.TableVisualization,
        instance: undefined   // created from setGraphMode()
      },
      'multiBarChart': {
        class: zeppelin.BarchartVisualization,
        instance: undefined
      },
      'pieChart': {
        class: zeppelin.PiechartVisualization,
        instance: undefined
      },
      'stackedAreaChart': {
        class: zeppelin.AreachartVisualization,
        instance: undefined
      },
      'lineChart': {
        class: zeppelin.LinechartVisualization,
        instance: undefined
      },
      'scatterChart': {
        class: zeppelin.ScatterchartVisualization,
        instance: undefined
      }
    };

    // type
    $scope.type;

    // Data of the result
    var data;

    // config
    $scope.config;

    // resultId = paragraph.id + index
    $scope.id;

    // referece to paragraph
    var paragraph;

    // index of the result
    var resultIndex;

    // TableData instance
    var tableData;

    // available columns in tabledata
    $scope.tableDataColumns = [];

    // enable helium
    var enableHelium = false;

    // graphMode
    $scope.graphMode;

    // image data
    $scope.imageData;

    $scope.init = function(result, config, paragraph, index) {
      console.log('result controller init %o %o %o', result, config, index);
      updateData(result, config, paragraph, index);
      renderResult($scope.type);
    };

    $scope.$on('updateResult', function(event, result, newConfig, paragraphRef, index) {
      if (paragraph.id !== paragraphRef.id || index !== resultIndex) {
        return;
      }
      console.log('updateResult %o %o %o %o', result, newConfig, paragraphRef, index);
      var refresh = !angular.equals(newConfig, $scope.config) ||
                    !angular.equals(result.type, $scope.type) ||
                    !angular.equals(result.data, data);

      updateData(result, newConfig, paragraph, resultIndex);
      renderResult($scope.type, refresh);
    });

    $scope.$on('appendParagraphOutput', function(event, data) {
      /* It has been observed that append events
       * can be errorneously called even if paragraph
       * execution has ended, and in that case, no append
       * should be made. Also, it was observed that between PENDING
       * and RUNNING states, append-events can be called and we can't
       * miss those, else during the length of paragraph run, few
       * initial output line/s will be missing.
       */
      if (paragraph.id === data.paragraphId &&
          resultIndex === data.index &&
         (paragraph.status === 'RUNNING' || paragraph.status === 'PENDING')) {
        appendTextOutput(data.data);
      }
    });

    $scope.$on('updateParagraphOutput', function(event, data) {
      if (paragraph.id === data.paragraphId &&
          resultIndex === data.index) {
        clearTextOutput();
        appendTextOutput(data.data);
      }
    });

    var updateData = function(result, config, paragraphRef, index) {
      data = result.data;
      paragraph = paragraphRef;
      resultIndex = parseInt(index);

      $scope.id = paragraph.id + '_' + index;
      $scope.type = result.type;
      config = config ? config : {};

      // initialize default config values
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

      $scope.graphMode = config.graph.mode;
      $scope.config = angular.copy(config);

      // enable only when it is last result
      enableHelium = (index === paragraphRef.results.msg.length - 1);

      if ($scope.type === 'TABLE') {
        var TableData = zeppelin.TableData;
        tableData = new TableData();
        tableData.loadParagraphResult({type: $scope.type, msg: data});
        $scope.tableDataColumns = tableData.columns;
        $scope.tableDataComment = tableData.comment;
      } else if ($scope.type === 'IMG') {
        $scope.imageData = data;
      }
    };

    var renderResult = function(type, refresh) {
      var activeApp;
      if (enableHelium) {
        getSuggestions();
        getApplicationStates();
        activeApp = _.get($scope.config, 'helium.activeApp');
      }

      if (activeApp) {
        var app = _.find($scope.apps, {id: activeApp});
        renderApp(app);
      } else {
        if (type === 'TABLE') {
          $scope.renderGraph($scope.graphMode, refresh);
        } else if (type === 'HTML') {
          renderHtml();
        } else if (type === 'ANGULAR') {
          renderAngular();
        } else if (type === 'TEXT') {
          renderText();
        }
      }
    };

    var renderHtml = function() {
      var retryRenderer = function() {
        var htmlEl = angular.element('#p' + $scope.id + '_html');
        if (htmlEl.length) {
          try {
            htmlEl.html(data);

            htmlEl.find('pre code').each(function(i, e) {
              hljs.highlightBlock(e);
            });
            /*eslint new-cap: [2, {"capIsNewExceptions": ["MathJax.Hub.Queue"]}]*/
            MathJax.Hub.Queue(['Typeset', MathJax.Hub, htmlEl[0]]);
          } catch (err) {
            console.log('HTML rendering error %o', err);
          }
        } else {
          $timeout(retryRenderer, 10);
        }
      };
      $timeout(retryRenderer);
    };

    var renderAngular = function() {
      var retryRenderer = function() {
        if (angular.element('#p' + $scope.id + '_angular').length) {
          try {
            angular.element('#p' + $scope.id + '_angular').html(data);

            var paragraphScope = noteVarShareService.get(paragraph.id + '_paragraphScope');
            $compile(angular.element('#p' + $scope.id + '_angular').contents())(paragraphScope);
          } catch (err) {
            console.log('ANGULAR rendering error %o', err);
          }
        } else {
          $timeout(retryRenderer, 10);
        }
      };
      $timeout(retryRenderer);
    };

    var textRendererInitialized = false;
    var renderText = function() {
      var retryRenderer = function() {
        var textEl = angular.element('#p' + $scope.id + '_text');
        if (textEl.length) {
          // clear all lines before render
          clearTextOutput();
          textRendererInitialized = true;

          if (data) {
            appendTextOutput(data);
          } else {
            flushAppendQueue();
          }

          angular.element('#p' + $scope.id + '_text').bind('mousewheel', function(e) {
            $scope.keepScrollDown = false;
          });
        } else {
          $timeout(retryRenderer, 10);
        }
      };
      $timeout(retryRenderer);
    };

    var clearTextOutput = function() {
      var textEl = angular.element('#p' + $scope.id + '_text');
      if (textEl.length) {
        textEl.children().remove();
      }
    };

    var textAppendQueueBeforeInitialize = [];

    var flushAppendQueue = function() {
      while (textAppendQueueBeforeInitialize.length > 0) {
        appendTextOutput(textAppendQueueBeforeInitialize.pop());
      }
    };

    var appendTextOutput = function(msg) {
      if (!textRendererInitialized) {
        textAppendQueueBeforeInitialize.push(msg);
      } else {
        flushAppendQueue();
        var textEl = angular.element('#p' + $scope.id + '_text');
        if (textEl.length) {
          var lines = msg.split('\n');
          for (var i = 0; i < lines.length; i++) {
            textEl.append(angular.element('<div></div>').text(lines[i]));
          }
        }
        if ($scope.keepScrollDown) {
          var doc = angular.element('#p' + $scope.id + '_text');
          doc[0].scrollTop = doc[0].scrollHeight;
        }
      }
    };

    $scope.renderGraph = function(type, refresh) {
      // set graph height
      var height = $scope.config.graph.height;
      var graphContainerEl = angular.element('#p' + $scope.id + '_graph');
      graphContainerEl.height(height);

      if (!type) {
        type = 'table';
      }

      var builtInViz = builtInVisualizations[type];
      if (builtInViz) {
        // deactive previsouly active visualization
        for (var t in builtInVisualizations) {
          var v = builtInVisualizations[t].instance;

          if (t !== type && v && v.isActive()) {
            v.deactivate();
            break;
          }
        }

        if (!builtInViz.instance) { // not instantiated yet
          // render when targetEl is available
          var retryRenderer = function() {
            var targetEl = angular.element('#p' + $scope.id + '_' + type);
            var transformationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + type);
            var visualizationSettingTargetEl = angular.element('#vizsetting' + $scope.id + '_' + type);
            if (targetEl.length) {
              try {
                // set height
                targetEl.height(height);

                // instantiate visualization
                var config = getVizConfig(type);
                var Visualization = builtInViz.class;
                builtInViz.instance = new Visualization(targetEl, config);

                // inject emitter, $templateRequest
                var emitter = function(graphSetting) {
                  commitVizConfigChange(graphSetting, type);
                };
                builtInViz.instance._emitter = emitter;
                builtInViz.instance._compile = $compile;
                builtInViz.instance._createNewScope = createNewScope;
                var transformation = builtInViz.instance.getTransformation();
                transformation._emitter = emitter;
                transformation._templateRequest = $templateRequest;
                transformation._compile = $compile;
                transformation._createNewScope = createNewScope;

                // render
                var transformed = transformation.transform(tableData);
                transformation.renderSetting(transformationSettingTargetEl);
                builtInViz.instance.render(transformed);
                builtInViz.instance.renderSetting(visualizationSettingTargetEl);
                builtInViz.instance.activate();
                angular.element(window).resize(function() {
                  builtInViz.instance.resize();
                });
              } catch (err) {
                console.log('Graph drawing error %o', err);
              }
            } else {
              $timeout(retryRenderer, 10);
            }
          };
          $timeout(retryRenderer);
        } else if (refresh) {
          console.log('Refresh data %o', tableData);
          // when graph options or data are changed
          var retryRenderer = function() {
            var targetEl = angular.element('#p' + $scope.id + '_' + type);
            var transformationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + type);
            var visualizationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + type);
            if (targetEl.length) {
              var config = getVizConfig(type);
              targetEl.height(height);
              var transformation = builtInViz.instance.getTransformation();
              transformation.setConfig(config);
              var transformed = transformation.transform(tableData);
              transformation.renderSetting(transformationSettingTargetEl);
              builtInViz.instance.setConfig(config);
              builtInViz.instance.render(transformed);
              builtInViz.instance.renderSetting(visualizationSettingTargetEl);
            } else {
              $timeout(retryRenderer, 10);
            }
          };
          $timeout(retryRenderer);
        } else {
          var retryRenderer = function() {
            var targetEl = angular.element('#p' + $scope.id + '_' + type);
            if (targetEl.length) {
              targetEl.height(height);
              builtInViz.instance.activate();
            } else {
              $timeout(retryRenderer, 10);
            }
          };
          $timeout(retryRenderer);
        }
      }
    };
    $scope.switchViz = function(newMode) {
      var newConfig = angular.copy($scope.config);
      var newParams = angular.copy(paragraph.settings.params);

      // graph options
      newConfig.graph.mode = newMode;

      // see switchApp()
      _.set(newConfig, 'helium.activeApp', undefined);

      commitParagraphResult(paragraph.title, paragraph.text, newConfig, newParams);
    };

    var createNewScope = function() {
      return $rootScope.$new(true);
    };

    var commitParagraphResult = function(title, text, config, params) {
      var newParagraphConfig = angular.copy(paragraph.config);
      newParagraphConfig.results = newParagraphConfig.results || [];
      newParagraphConfig.results[resultIndex] = config;
      websocketMsgSrv.commitParagraph(paragraph.id, title, text, newParagraphConfig, params);
    };

    $scope.toggleGraphSetting = function() {
      var newConfig = angular.copy($scope.config);
      if (newConfig.graph.optionOpen) {
        newConfig.graph.optionOpen = false;
      } else {
        newConfig.graph.optionOpen = true;
      }
      var newParams = angular.copy(paragraph.settings.params);

      commitParagraphResult(paragraph.title, paragraph.text, newConfig, newParams);
    };

    var getVizConfig = function(vizId) {
      var config;
      var graph = $scope.config.graph;
      if (graph) {
        // copy setting for vizId
        if (graph.setting) {
          config = angular.copy(graph.setting[vizId]);
        }

        if (!config) {
          config = {};
        }

        // copy common setting
        config.common = angular.copy(graph.commonSetting) || {};

        // copy pivot setting
        if (graph.keys) {
          config.common.pivot = {
            keys: angular.copy(graph.keys),
            groups: angular.copy(graph.groups),
            values: angular.copy(graph.values)
          };
        }
      }
      console.log('getVizConfig', config);
      return config;
    };

    var commitVizConfigChange = function(config, vizId) {
      var newConfig = angular.copy($scope.config);
      if (!newConfig.graph) {
        newConfig.graph = {};
      }

      // copy setting for vizId
      if (!newConfig.graph.setting) {
        newConfig.graph.setting = {};
      }
      newConfig.graph.setting[vizId] = angular.copy(config);

      // copy common setting
      if (newConfig.graph.setting[vizId]) {
        newConfig.graph.commonSetting = newConfig.graph.setting[vizId].common;
        delete newConfig.graph.setting[vizId].common;
      }

      // copy pivot setting
      if (newConfig.graph.commonSetting && newConfig.graph.commonSetting.pivot) {
        newConfig.graph.keys = newConfig.graph.commonSetting.pivot.keys;
        newConfig.graph.groups = newConfig.graph.commonSetting.pivot.groups;
        newConfig.graph.values = newConfig.graph.commonSetting.pivot.values;
        delete newConfig.graph.commonSetting.pivot;
      }
      console.log('committVizConfig', newConfig);
      var newParams = angular.copy(paragraph.settings.params);
      commitParagraphResult(paragraph.title, paragraph.text, newConfig, newParams);
    };

    $scope.$on('paragraphResized', function(event, paragraphId) {
      // paragraph col width changed
      if (paragraphId === paragraph.id) {
        var builtInViz = builtInVisualizations[$scope.graphMode];
        if (builtInViz && builtInViz.instance) {
          builtInViz.instance.resize();
        }
      }
    });

    $scope.resize = function(width, height) {
      $timeout(function() {
        changeHeight(width, height);
      }, 200);
    };

    var changeHeight = function(width, height) {
      var newParams = angular.copy(paragraph.settings.params);
      var newConfig = angular.copy($scope.config);

      newConfig.graph.height = height;
      paragraph.config.colWidth = width;

      commitParagraphResult(paragraph.title, paragraph.text, newConfig, newParams);
    };

    $scope.exportToDSV = function(delimiter) {
      var dsv = '';
      for (var titleIndex in tableData.columns) {
        dsv += tableData.columns[titleIndex].name + delimiter;
      }
      dsv = dsv.substring(0, dsv.length - 1) + '\n';
      for (var r in tableData.rows) {
        var row = tableData.rows[r];
        var dsvRow = '';
        for (var index in row) {
          var stringValue =  (row[index]).toString();
          if (stringValue.contains(delimiter)) {
            dsvRow += '"' + stringValue + '"' + delimiter;
          } else {
            dsvRow += row[index] + delimiter;
          }
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

    $scope.getBase64ImageSrc = function(base64Data) {
      return 'data:image/png;base64,' + base64Data;
    };

    // Helium ----------------
    var ANGULAR_FUNCTION_OBJECT_NAME_PREFIX = '_Z_ANGULAR_FUNC_';

    // app states
    $scope.apps = [];

    // suggested apps
    $scope.suggestion = {};

    $scope.switchApp = function(appId) {
      var newConfig = angular.copy($scope.config);
      var newParams = angular.copy(paragraph.settings.params);

      // 'helium.activeApp' can be cleared by switchViz()
      _.set(newConfig, 'helium.activeApp', appId);

      commitConfig(newConfig, newParams);
    };

    $scope.loadApp = function(heliumPackage) {
      var noteId = $route.current.pathParams.noteId;
      $http.post(baseUrlSrv.getRestApiBase() + '/helium/load/' + noteId + '/' + paragraph.id, heliumPackage)
        .success(function(data, status, headers, config) {
          console.log('Load app %o', data);
        })
        .error(function(err, status, headers, config) {
          console.log('Error %o', err);
        });
    };

    var commitConfig = function(config, params) {
      commitParagraphResult(paragraph.title, paragraph.text, config, params);
    };

    var getApplicationStates = function() {
      var appStates = [];

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
      $http.get(baseUrlSrv.getRestApiBase() + '/helium/suggest/' + noteId + '/' + paragraph.id)
        .success(function(data, status, headers, config) {
          $scope.suggestion = data.body;
        })
        .error(function(err, status, headers, config) {
          console.log('Error %o', err);
        });
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
      if (paragraph.id === data.paragraphId) {
        var app = _.find($scope.apps, {id: data.appId});
        if (app) {
          app.output += data.data;

          var paragraphAppState = _.find(paragraph.apps, {id: data.appId});
          paragraphAppState.output = app.output;

          var targetEl = angular.element(document.getElementById('p' + app.id));
          targetEl.html(app.output);
          $compile(targetEl.contents())(getAppScope(app));
          console.log('append app output %o', $scope.apps);
        }
      }
    });

    $scope.$on('updateAppOutput', function(event, data) {
      if (paragraph.id === data.paragraphId) {
        var app = _.find($scope.apps, {id: data.appId});
        if (app) {
          app.output = data.data;

          var paragraphAppState = _.find(paragraph.apps, {id: data.appId});
          paragraphAppState.output = app.output;

          var targetEl = angular.element(document.getElementById('p' + app.id));
          targetEl.html(app.output);
          $compile(targetEl.contents())(getAppScope(app));
          console.log('append app output');
        }
      }
    });

    $scope.$on('appLoad', function(event, data) {
      if (paragraph.id === data.paragraphId) {
        var app = _.find($scope.apps, {id: data.appId});
        if (!app) {
          app = {
            id: data.appId,
            pkg: data.pkg,
            status: 'UNLOADED',
            output: ''
          };

          $scope.apps.push(app);
          paragraph.apps.push(app);
          $scope.switchApp(app.id);
        }
      }
    });

    $scope.$on('appStatusChange', function(event, data) {
      if (paragraph.id === data.paragraphId) {
        var app = _.find($scope.apps, {id: data.appId});
        if (app) {
          app.status = data.status;
          var paragraphAppState = _.find(paragraph.apps, {id: data.appId});
          paragraphAppState.status = app.status;
        }
      }
    });

    var getAppRegistry = function(appState) {
      if (!appState.registry) {
        appState.registry = {};
      }

      return appState.registry;
    };

    var getAppScope = function(appState) {
      if (!appState.scope) {
        appState.scope = $rootScope.$new(true, $rootScope);
      }
      return appState.scope;
    };

    $scope.$on('angularObjectUpdate', function(event, data) {
      var noteId = $route.current.pathParams.noteId;
      if (!data.noteId || data.noteId === noteId) {
        var scope;
        var registry;

        var app = _.find($scope.apps, {id: data.paragraphId});
        if (app) {
          scope = getAppScope(app);
          registry = getAppRegistry(app);
        } else {
          // no matching app in this paragraph
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

        var app = _.find($scope.apps, {id: data.paragraphId});
        if (app) {
          scope = getAppScope(app);
          registry = getAppRegistry(app);
        } else {
          // no matching app in this paragraph
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
  };
})();
