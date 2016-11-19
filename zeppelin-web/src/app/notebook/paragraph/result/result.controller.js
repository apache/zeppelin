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
    'websocketMsgSrv',
    'baseUrlSrv',
    'ngToast',
    'saveAsService'
  ];

  function ResultCtrl($scope, $rootScope, $route, $window, $routeParams, $location,
                         $timeout, $compile, $http, $q, websocketMsgSrv,
                         baseUrlSrv, ngToast, saveAsService) {

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
    var resultIndex

    // TableData instance
    var tableData;

    // available columns in tabledata
    $scope.tableDataColumns = [];

    // graphMode
    $scope.graphMode;
    
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
      
      /*
      var oldType = $scope.getResultType();
      var newType = $scope.getResultType(data.paragraph);
      var oldGraphMode = $scope.getGraphMode();
      var newGraphMode = $scope.getGraphMode(data.paragraph);
      var oldActiveApp = _.get($scope.paragraph.config, 'helium.activeApp');
      var newActiveApp = _.get(data.paragraph.config, 'helium.activeApp');
      */

      updateData(result, newConfig, paragraph, resultIndex);
      renderResult($scope.type, true);
    });

    var updateData = function(result, config, paragraphRef, index) {
      data = result.data;
      paragraph = paragraphRef;
      resultIndex = parseInt(index);

      $scope.id = paragraph.id + "_" + index;
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

      $scope.graphMode = config.graph.mode;
      $scope.config = angular.copy(config);

      if ($scope.type === 'TABLE') {
        var TableData = zeppelin.TableData;
        tableData = new TableData();
        tableData.loadParagraphResult({type: $scope.type, msg: data});
        $scope.tableDataColumns = tableData.columns;
        $scope.tableDataComment = tableData.comment;
      }
    };

    var renderResult = function(type, refresh) {
      if (type === 'TABLE') {
        $scope.renderGraph($scope.graphMode, refresh);
      } else if (type === 'HTML') {
        renderHtml();
      } else if (type === 'ANGULAR') {
        renderAngular();
      } else if (type === 'TEXT') {
        renderText();
      }

      /*
      getApplicationStates();
      getSuggestions();

      if (newActiveApp && newActiveApp !== oldActiveApp) {
        var app = _.find($scope.apps, {id: newActiveApp});
        renderApp(app);
      }
      */
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

    var renderText = function() {
      var retryRenderer = function() {
        var textEl = angular.element('#p' + $scope.id + '_text');
        if (textEl.length) {
          // clear all lines before render
          clearTextOutput();

          if (data) {
            appendTextOutput(data);
          }

          angular.element('#p' + $scope.id + '_text').bind('mousewheel', function(e) {
            $scope.keepScrollDown = false;
          });
          $scope.flushStreamingOutput = true;
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

    var appendTextOutput = function(msg) {
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
    };

    $scope.renderGraph = function(type, refresh) {
      clearUnknownColsFromGraphOption();
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

            if (targetEl.length) {
              try {
                // set height
                targetEl.height(height);

                // instantiate visualization
                var Visualization = builtInViz.class;
                builtInViz.instance = new Visualization(targetEl, $scope.config.graph);
                builtInViz.instance.render(tableData);
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
            if (targetEl.length) {
              targetEl.height(height);
              builtInViz.instance.setConfig($scope.config.graph);
              builtInViz.instance.render(tableData);
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

    var commitParagraphResult = function(title, text, config, params) {
      var newParagraphConfig = angular.copy(paragraph.config);
      newParagraphConfig.result = newParagraphConfig.result || [];
      newParagraphConfig.result[resultIndex] = config;
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

    var commitConfigChange = function(config) {
      clearUnknownColsFromGraphOption();
      var newConfig = angular.copy(config);
      var newParams = angular.copy(paragraph.settings.params);

      commitParagraphResult(paragraph.title, paragraph.text, newConfig, newParams);
    };

    $scope.onGraphOptionChange = function() {
      commitConfigChange($scope.config);
    };

    $scope.removeGraphOptionKeys = function(idx) {
      $scope.config.graph.keys.splice(idx, 1);
      commitConfigChange($scope.config);
    };

    $scope.removeGraphOptionValues = function(idx) {
      $scope.config.graph.values.splice(idx, 1);
      commitConfigChange($scope.config);
    };

    $scope.removeGraphOptionGroups = function(idx) {
      $scope.config.graph.groups.splice(idx, 1);
      commitConfigChange($scope.config);
    };

    $scope.setGraphOptionValueAggr = function(idx, aggr) {
      $scope.config.graph.values[idx].aggr = aggr;
      commitConfigChange($scope.config);
    };

    $scope.removeScatterOptionXaxis = function(idx) {
      $scope.config.graph.scatter.xAxis = null;
      commitConfigChange($scope.config);
    };

    $scope.removeScatterOptionYaxis = function(idx) {
      $scope.config.graph.scatter.yAxis = null;
      commitConfigChange($scope.config);
    };

    $scope.removeScatterOptionGroup = function(idx) {
      $scope.config.graph.scatter.group = null;
      commitConfigChange($scope.config);
    };

    $scope.removeScatterOptionSize = function(idx) {
      $scope.config.graph.scatter.size = null;
      commitConfigChange($scope.config);
    };

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
          for (var j = 0; j < tableData.columns.length; j++) {
            var a = list[i];
            var b = tableData.columns[j];
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

      var removeUnknownFromFields = function(fields) {
        for (var f in fields) {
          if (fields[f]) {
            var found = false;
            for (var i = 0; i < tableData.columns.length; i++) {
              var a = fields[f];
              var b = tableData.columns[i];
              if (a.index === b.index && a.name === b.name) {
                found = true;
                break;
              }
            }
            if (!found && (fields[f] instanceof Object) && !(fields[f] instanceof Array)) {
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

      removeUnknownFromFields($scope.paragraph.config.graph.scatter);
    };

    /* select default key and value if there're none selected */
    var selectDefaultColsForGraphOption = function() {
      if ($scope.paragraph.config.graph.keys.length === 0 && tableData.columns.length > 0) {
        $scope.paragraph.config.graph.keys.push(tableData.columns[0]);
      }

      if ($scope.paragraph.config.graph.values.length === 0 && tableData.columns.length > 1) {
        $scope.paragraph.config.graph.values.push(tableData.columns[1]);
      }

      if (!$scope.paragraph.config.graph.scatter.xAxis && !$scope.paragraph.config.graph.scatter.yAxis) {
        if (tableData.columns.length > 1) {
          $scope.paragraph.config.graph.scatter.xAxis = tableData.columns[0];
          $scope.paragraph.config.graph.scatter.yAxis = tableData.columns[1];
        } else if (tableData.columns.length === 1) {
          $scope.paragraph.config.graph.scatter.xAxis = tableData.columns[0];
        }
      }
    };

    $scope.isValidSizeOption = function(options) {
      var builtInViz = builtInVisualizations.scatterChart;
      if (builtInViz && builtInViz.instance) {
        return builtInViz.instance.isValidSizeOption(options);
      } else {
        return false;
      }
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
  };
})();
