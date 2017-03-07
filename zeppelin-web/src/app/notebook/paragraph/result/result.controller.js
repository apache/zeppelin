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

import TableData from '../../../tabledata/tabledata';
import TableVisualization from '../../../visualization/builtins/visualization-table';
import BarchartVisualization from '../../../visualization/builtins/visualization-barchart';
import PiechartVisualization from '../../../visualization/builtins/visualization-piechart';
import AreachartVisualization from '../../../visualization/builtins/visualization-areachart';
import LinechartVisualization from '../../../visualization/builtins/visualization-linechart';
import ScatterchartVisualization from '../../../visualization/builtins/visualization-scatterchart';
import {
  DefaultDisplayType,
  SpellResult,
} from '../../../spell'
import { ParagraphStatus, } from '../paragraph.status';

angular.module('zeppelinWebApp').controller('ResultCtrl', ResultCtrl);

function ResultCtrl($scope, $rootScope, $route, $window, $routeParams, $location,
                    $timeout, $compile, $http, $q, $templateRequest, $sce, websocketMsgSrv,
                    baseUrlSrv, ngToast, saveAsService, noteVarShareService, heliumService) {
  'ngInject';

  /**
   * Built-in visualizations
   */
  $scope.builtInTableDataVisualizationList = [
    {
      id: 'table',   // paragraph.config.graph.mode
      name: 'Table', // human readable name. tooltip
      icon: '<i class="fa fa-table"></i>'
    },
    {
      id: 'multiBarChart',
      name: 'Bar Chart',
      icon: '<i class="fa fa-bar-chart"></i>',
      transformation: 'pivot'
    },
    {
      id: 'pieChart',
      name: 'Pie Chart',
      icon: '<i class="fa fa-pie-chart"></i>',
      transformation: 'pivot'
    },
    {
      id: 'stackedAreaChart',
      name: 'Area Chart',
      icon: '<i class="fa fa-area-chart"></i>',
      transformation: 'pivot'
    },
    {
      id: 'lineChart',
      name: 'Line Chart',
      icon: '<i class="fa fa-line-chart"></i>',
      transformation: 'pivot'
    },
    {
      id: 'scatterChart',
      name: 'Scatter Chart',
      icon: '<i class="cf cf-scatter-chart"></i>'
    }
  ];

  /**
   * Holds class and actual runtime instance and related infos of built-in visualizations
   */
  var builtInVisualizations = {
    'table': {
      class: TableVisualization,
      instance: undefined   // created from setGraphMode()
    },
    'multiBarChart': {
      class: BarchartVisualization,
      instance: undefined
    },
    'pieChart': {
      class: PiechartVisualization,
      instance: undefined
    },
    'stackedAreaChart': {
      class: AreachartVisualization,
      instance: undefined
    },
    'lineChart': {
      class: LinechartVisualization,
      instance: undefined
    },
    'scatterChart': {
      class: ScatterchartVisualization,
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

  // queue for append output
  const textResultQueueForAppend = [];

  $scope.init = function(result, config, paragraph, index) {
    // register helium plugin vis
    var visBundles = heliumService.getVisualizationBundles();
    visBundles.forEach(function(vis) {
      $scope.builtInTableDataVisualizationList.push({
        id: vis.id,
        name: vis.name,
        icon: $sce.trustAsHtml(vis.icon)
      });
      builtInVisualizations[vis.id] = {
        class: vis.class
      };
    });

    updateData(result, config, paragraph, index);
    renderResult($scope.type);
  };

  function isDOMLoaded(targetElemId) {
    const elem = angular.element(`#${targetElemId}`);
    return elem.length;
  }

  function retryUntilElemIsLoaded(targetElemId, callback) {
    function retry() {
      if (!isDOMLoaded(targetElemId)) {
        $timeout(retry, 10);
        return;
      }

      const elem = angular.element(`#${targetElemId}`);
      callback(elem);
    }

    $timeout(retry);
  }

  $scope.$on('updateResult', function(event, result, newConfig, paragraphRef, index) {
    if (paragraph.id !== paragraphRef.id || index !== resultIndex) {
      return;
    }

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
      (paragraph.status === ParagraphStatus.PENDING || paragraph.status === ParagraphStatus.RUNNING)) {

      if (DefaultDisplayType.TEXT !== $scope.type) {
        $scope.type = DefaultDisplayType.TEXT;
      }
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
      tableData = new TableData();
      tableData.loadParagraphResult({type: $scope.type, msg: data});
      $scope.tableDataColumns = tableData.columns;
      $scope.tableDataComment = tableData.comment;
    } else if ($scope.type === 'IMG') {
      $scope.imageData = data;
    }
  };

  $scope.createDisplayDOMId = function(baseDOMId, type) {
    if (type === DefaultDisplayType.TABLE) {
      return `${baseDOMId}_graph`;
    } else if (type === DefaultDisplayType.HTML) {
      return `${baseDOMId}_html`;
    } else if (type === DefaultDisplayType.ANGULAR) {
      return `${baseDOMId}_angular`;
    } else if (type === DefaultDisplayType.TEXT) {
      return `${baseDOMId}_text`;
    } else if (type === DefaultDisplayType.ELEMENT) {
      return `${baseDOMId}_elem`;
    } else {
      console.error(`Cannot create display DOM Id due to unknown display type: ${type}`);
    }
  };

  $scope.renderDefaultDisplay = function(targetElemId, type, data, refresh) {
    const afterLoaded = () => {
      if (type === DefaultDisplayType.TABLE) {
        renderGraph(targetElemId, $scope.graphMode, refresh);
      } else if (type === DefaultDisplayType.HTML) {
        renderHtml(targetElemId, data);
      } else if (type === DefaultDisplayType.ANGULAR) {
        renderAngular(targetElemId, data);
      } else if (type === DefaultDisplayType.TEXT) {
        renderText(targetElemId, data);
      } else if (type === DefaultDisplayType.ELEMENT) {
        renderElem(targetElemId, data);
      } else {
        console.error(`Unknown Display Type: ${type}`);
      }
    }

    retryUntilElemIsLoaded(targetElemId, afterLoaded);

    // send message to parent that this result is rendered
    const paragraphId = $scope.$parent.paragraph.id;
    $scope.$emit('resultRendered', paragraphId);
  };

  const renderResult = function(type, refresh) {
    let activeApp;
    if (enableHelium) {
      getSuggestions();
      getApplicationStates();
      activeApp = _.get($scope.config, 'helium.activeApp');
    }

    if (activeApp) {
      const appState = _.find($scope.apps, {id: activeApp});
      renderApp(`p${appState.id}`, appState);
    } else {
      if (!DefaultDisplayType[type]) {
        $scope.renderCustomDisplay(type, data);
      } else {
        const targetElemId = $scope.createDisplayDOMId(`p${$scope.id}`, type);
        $scope.renderDefaultDisplay(targetElemId, type, data, refresh);
      }
    }
  };

  $scope.isDefaultDisplay = function() {
    return DefaultDisplayType[$scope.type];
  };

  /**
   * Render multiple sub results for custom display
   */
  $scope.renderCustomDisplay = function(type, data) {
    // get result from intp
    if (!heliumService.getSpellByMagic(type)) {
      console.error(`Can't execute spell due to unknown display type: ${type}`);
      return;
    }

    // custom display result can include multiple subset results
    heliumService.executeSpellAsDisplaySystem(type, data)
      .then(dataWithTypes => {
        const containerDOMId = `p${$scope.id}_custom`;
        const afterLoaded = () => {
          const containerDOM = angular.element(`#${containerDOMId}`);
          // Spell.interpret() can create multiple outputs
          for(let i = 0; i < dataWithTypes.length; i++) {
            const dt = dataWithTypes[i];
            const data = dt.data;
            const type = dt.type;

            // prepare each DOM to be filled
            const subResultDOMId = $scope.createDisplayDOMId(`p${$scope.id}_custom_${i}`, type);
            const subResultDOM = document.createElement('div');
            containerDOM.append(subResultDOM);
            subResultDOM.setAttribute('id', subResultDOMId);

            $scope.renderDefaultDisplay(subResultDOMId, type, data, true);
          }
        };

        retryUntilElemIsLoaded(containerDOMId, afterLoaded);
      })
      .catch(error => {
        console.error(`Failed to render custom display: ${$scope.type}\n` + error);
      });
  };

  /**
   * generates actually object which will be consumed from `data` property
   * feed it to the success callback.
   * if error occurs, the error is passed to the failure callback
   *
   * @param data {Object or Function}
   * @param type {string} Display Type
   * @param successCallback
   * @param failureCallback
   */
  const handleData = function(data, type, successCallback, failureCallback) {
    if (SpellResult.isFunction(data)) {
      try {
        successCallback(data());
      } catch (error) {
        failureCallback(error);
        console.error(`Failed to handle ${type} type, function data\n`, error);
      }
    } else if (SpellResult.isObject(data)) {
      try {
        successCallback(data);
      } catch (error) {
        console.error(`Failed to handle ${type} type, object data\n`, error);
      }
    }
  };

  const renderElem = function(targetElemId, data) {
    const elem = angular.element(`#${targetElemId}`);
    handleData(() => { data(targetElemId) }, DefaultDisplayType.ELEMENT,
      () => {}, /** HTML element will be filled with data. thus pass empty success callback */
      (error) => { elem.html(`${error.stack}`); }
    );
  };

  const renderHtml = function(targetElemId, data) {
    const elem = angular.element(`#${targetElemId}`);
    handleData(data, DefaultDisplayType.HTML,
      (generated) => {
        elem.html(generated);
        elem.find('pre code').each(function(i, e) {
          hljs.highlightBlock(e);
        });
        /*eslint new-cap: [2, {"capIsNewExceptions": ["MathJax.Hub.Queue"]}]*/
        MathJax.Hub.Queue(['Typeset', MathJax.Hub, elem[0]]);
      },
      (error) => {  elem.html(`${error.stack}`); }
    );
  };

  const renderAngular = function(targetElemId, data) {
    const elem = angular.element(`#${targetElemId}`);
    const paragraphScope = noteVarShareService.get(`${paragraph.id}_paragraphScope`);
    handleData(data, DefaultDisplayType.ANGULAR,
      (generated) => {
        elem.html(generated);
        $compile(elem.contents())(paragraphScope);
      },
      (error) => {  elem.html(`${error.stack}`); }
    );
  };

  const getTextResultElemId = function (resultId) {
    return `p${resultId}_text`;
  };

  const renderText = function(targetElemId, data) {
    const elem = angular.element(`#${targetElemId}`);
    handleData(data, DefaultDisplayType.TEXT,
      (generated) => {
        // clear all lines before render
        removeChildrenDOM(targetElemId);

        if (generated) {
          const divDOM = angular.element('<div></div>').text(generated);
          elem.append(divDOM);
        }

        elem.bind('mousewheel', (e) => { $scope.keepScrollDown = false; });
      },
      (error) => {  elem.html(`${error.stack}`); }
    );
  };

  const removeChildrenDOM = function(targetElemId) {
    const elem = angular.element(`#${targetElemId}`);
    if (elem.length) {
      elem.children().remove();
    }
  };

  function appendTextOutput(data) {
    const elemId = getTextResultElemId($scope.id);
    textResultQueueForAppend.push(data);

    // if DOM is not loaded, just push data and return
    if (!isDOMLoaded(elemId)) {
      return;
    }

    const elem = angular.element(`#${elemId}`);

    // pop all stacked data and append to the DOM
    while (textResultQueueForAppend.length > 0) {
      const line = textResultQueueForAppend.pop();
      elem.append(angular.element('<div></div>').text(line));

      if ($scope.keepScrollDown) {
        const doc = angular.element(`#${elemId}`);
        doc[0].scrollTop = doc[0].scrollHeight;
      }
    }
  }

  const renderGraph = function(graphElemId, graphMode, refresh) {
    // set graph height
    const height = $scope.config.graph.height;
    const graphElem = angular.element(`#${graphElemId}`);
    graphElem.height(height);

    if (!graphMode) { graphMode = 'table'; }

    const builtInViz = builtInVisualizations[graphMode];
    if (!builtInViz) { return; }

    // deactive previsouly active visualization
    for (let t in builtInVisualizations) {
      const v = builtInVisualizations[t].instance;

      if (t !== graphMode && v && v.isActive()) {
        v.deactivate();
        break;
      }
    }

    let afterLoaded = function() { /** will be overwritten */ };

    if (!builtInViz.instance) { // not instantiated yet
      // render when targetEl is available
      afterLoaded = function(loadedElem) {
        try {
          const transformationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + graphMode);
          const visualizationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + graphMode);
          // set height
          loadedElem.height(height);

          // instantiate visualization
          const config = getVizConfig(graphMode);
          const Visualization = builtInViz.class;
          builtInViz.instance = new Visualization(loadedElem, config);

          // inject emitter, $templateRequest
          const emitter = function(graphSetting) {
            commitVizConfigChange(graphSetting, graphMode);
          };
          builtInViz.instance._emitter = emitter;
          builtInViz.instance._compile = $compile;
          builtInViz.instance._createNewScope = createNewScope;
          const transformation = builtInViz.instance.getTransformation();
          transformation._emitter = emitter;
          transformation._templateRequest = $templateRequest;
          transformation._compile = $compile;
          transformation._createNewScope = createNewScope;

          // render
          const transformed = transformation.transform(tableData);
          transformation.renderSetting(transformationSettingTargetEl);
          builtInViz.instance.render(transformed);
          builtInViz.instance.renderSetting(visualizationSettingTargetEl);
          builtInViz.instance.activate();
          angular.element(window).resize(() => {
            builtInViz.instance.resize();
          });
        } catch (err) {
          console.error('Graph drawing error %o', err);
        }
      };

    } else if (refresh) {
      // when graph options or data are changed
      console.log('Refresh data %o', tableData);

      afterLoaded = function(loadedElem) {
        const transformationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + graphMode);
        const visualizationSettingTargetEl = angular.element('#trsetting' + $scope.id + '_' + graphMode);
        const config = getVizConfig(graphMode);
        loadedElem.height(height);
        const transformation = builtInViz.instance.getTransformation();
        transformation.setConfig(config);
        const transformed = transformation.transform(tableData);
        transformation.renderSetting(transformationSettingTargetEl);
        builtInViz.instance.setConfig(config);
        builtInViz.instance.render(transformed);
        builtInViz.instance.renderSetting(visualizationSettingTargetEl);
      };

    } else {
      afterLoaded = function(loadedElem) {
        loadedElem.height(height);
        builtInViz.instance.activate();
      };
    }

    const tableElemId = `p${$scope.id}_${graphMode}`;
    retryUntilElemIsLoaded(tableElemId, afterLoaded);
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
    if ($scope.revisionView === true) {
      // local update without commit
      updateData({
        type: $scope.type,
        data: data
      }, newParagraphConfig.results[resultIndex], paragraph, resultIndex);
      renderResult($scope.type, true);
    } else {
      websocketMsgSrv.commitParagraph(paragraph.id, title, text, newParagraphConfig, params);
    }
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
    var dateFinished = moment(paragraph.dateFinished).format('YYYY-MM-DD hh:mm:ss A');
    var exportedFileName = paragraph.title ? paragraph.title + '_' + dateFinished : 'data_' + dateFinished;

    for (var titleIndex in tableData.columns) {
      dsv += tableData.columns[titleIndex].name + delimiter;
    }
    dsv = dsv.substring(0, dsv.length - 1) + '\n';
    for (var r in tableData.rows) {
      var row = tableData.rows[r];
      var dsvRow = '';
      for (var index in row) {
        var stringValue =  (row[index]).toString();
        if (stringValue.indexOf(delimiter) > -1) {
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
    saveAsService.saveAs(dsv, exportedFileName, extension);
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
    if (!noteId) {
    return;
    }
    $http.get(baseUrlSrv.getRestApiBase() + '/helium/suggest/' + noteId + '/' + paragraph.id)
      .success(function(data, status, headers, config) {
        $scope.suggestion = data.body;
      })
      .error(function(err, status, headers, config) {
        console.log('Error %o', err);
      });
  };

  const renderApp = function(targetElemId, appState) {
    const afterLoaded = (loadedElem) => {
      try {
        console.log('renderApp %o', appState);
        loadedElem.html(appState.output);
        $compile(loadedElem.contents())(getAppScope(appState));
      } catch (err) {
        console.log('App rendering error %o', err);
      }
    };
    retryUntilElemIsLoaded(targetElemId, afterLoaded);
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
}
