/* Copyright 2014 NFLabs
 *
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

/**
 * @ngdoc function
 * @name zeppelinWebApp.controller:ParagraphCtrl
 * @description
 * # ParagraphCtrl
 * Controller of the paragraph, manage everything related to the paragraph
 *
 * @author anthonycorbacho
 */
angular.module('zeppelinWebApp')
        .controller('ParagraphCtrl', function($scope, $rootScope, $route, $window, $element, $routeParams, $location, $timeout) {

  $scope.paragraph = null;
  $scope.editor = null;
  var editorMode = {scala: 'ace/mode/scala', sql: 'ace/mode/sql', markdown: 'ace/mode/markdown'};

  $scope.forms = {};

  // Controller init
  $scope.init = function(newParagraph) {
    $scope.paragraph = newParagraph;
    $scope.chart = {};
    $scope.colWidthOption = [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 ];
    $scope.showTitleEditor = false;

    initializeDefault();

    if ($scope.getResultType() === "TABLE") {
      $scope.lastData.settings = $scope.paragraph.settings;
      $scope.lastData.config = $scope.paragraph.config;
      $scope.loadTableData($scope.paragraph.result);
      $scope.setGraphMode($scope.getGraphMode(), false, false);
    }
  };

  $scope.getIframeDimensions = function () {
    if ($scope.asIframe) {
      var paragraphid = "#" + $routeParams.paragraphId + "_container";
      var height = $(paragraphid).height();
      return height;
    }
    return 0;
  };

  $scope.$watch($scope.getIframeDimensions, function (newValue, oldValue) {
    if ($scope.asIframe && newValue) {
      var message = {};
      message.height = newValue;
      message.url = $location.$$absUrl;
      $window.parent.postMessage(angular.toJson(message), "*");
    }
  });

  var initializeDefault = function(){
    if (!$scope.paragraph.config) {
      $scope.paragraph.config = {colWidth:12};
    } else if (!$scope.paragraph.config.colWidth) {
      $scope.paragraph.config.colWidth = 12;
    }

    if (!$scope.lastData) {
      $scope.lastData = {};
    }
  };

  $rootScope.$on('updateParagraph', function(event, data) {
    if (data.paragraph.id === $scope.paragraph.id &&
         (
             data.paragraph.dateCreated !== $scope.paragraph.dateCreated ||
             data.paragraph.dateFinished !== $scope.paragraph.dateFinished ||
             data.paragraph.dateStarted !== $scope.paragraph.dateStarted ||
             data.paragraph.status !== $scope.paragraph.status ||
             data.paragraph.jobName !== $scope.paragraph.jobName ||
             data.paragraph.title !== $scope.paragraph.title ||
             data.paragraph.errorMessage !== $scope.paragraph.errorMessage ||
             !angular.equals(data.paragraph.settings, $scope.lastData.settings) ||
             !angular.equals(data.paragraph.config, $scope.lastData.config)
         )
       ) {
      // store original data for comparison
      $scope.lastData.settings = jQuery.extend(true, {}, data.paragraph.settings);
      $scope.lastData.config = jQuery.extend(true, {}, data.paragraph.config);

      var oldType = $scope.getResultType();
      var newType = $scope.getResultType(data.paragraph);
      var oldGraphMode = $scope.getGraphMode();
      var newGraphMode = $scope.getGraphMode(data.paragraph);

      //console.log("updateParagraph oldData %o, newData %o. type %o -> %o, mode %o -> %o", $scope.paragraph, data, oldType, newType, oldGraphMode, newGraphMode);

      if ($scope.paragraph.text !== data.paragraph.text) {
        if ($scope.dirtyText) {         // check if editor has local update
          if ($scope.dirtyText === data.paragraph.text ) {  // when local update is the same from remote, clear local update
            $scope.paragraph.text = data.paragraph.text;
            $scope.dirtyText = undefined;
          } else { // if there're local update, keep it.
            $scope.paragraph.text = $scope.dirtyText;
          }
        } else {
          $scope.paragraph.text = data.paragraph.text;
        }
      }


      /** push the rest */
      $scope.paragraph.aborted = data.paragraph.aborted;
      $scope.paragraph.dateCreated = data.paragraph.dateCreated;
      $scope.paragraph.dateFinished = data.paragraph.dateFinished;
      $scope.paragraph.dateStarted = data.paragraph.dateStarted;
      $scope.paragraph.errorMessage = data.paragraph.errorMessage;
      $scope.paragraph.jobName = data.paragraph.jobName;
      $scope.paragraph.title = data.paragraph.title;
      $scope.paragraph.status = data.paragraph.status;
      $scope.paragraph.result = data.paragraph.result;
      $scope.paragraph.settings = data.paragraph.settings;
      
      if (!data.paragraph.config.asIframe) {
        initializeDefault();
        $scope.paragraph.config = data.paragraph.config;
        
        // update column class
        // TODO : do it in angualr way
        var el = $('#' + $scope.paragraph.id + "_paragraphColumn");
        var elMain = $('#' + $scope.paragraph.id + "_paragraphColumn_main");

        elMain.removeClass(elMain.attr('class'));
        if (!$scope.paragraph.config.colWidth) {
          $scope.paragraph.config.colWidth = 12;
        }
        elMain.addClass("paragraph-col col-md-" + $scope.paragraph.config.colWidth);

        el.removeClass(el.attr('class'))
        el.addClass("paragraph-space box paragraph-margin");
      }
      
      if (newType==="TABLE") {
        $scope.loadTableData($scope.paragraph.result);
        /** User changed the chart type? */
        if (oldGraphMode !== newGraphMode) {
          $scope.setGraphMode(newGraphMode, false, false);
        } else {
          $scope.setGraphMode(newGraphMode, false, true);
        }
      }
    }
  });

  $scope.isRunning = function(){
    if($scope.paragraph.status=='RUNNING' || $scope.paragraph.status=='PENDING') {
      return true;
    } else {
      return false;
    }
  };

  $scope.cancelParagraph = function() {
    console.log("Cancel %o", $scope.paragraph.id);
    var data = {op: 'CANCEL_PARAGRAPH', data: {id: $scope.paragraph.id }};
    $rootScope.$emit('sendNewEvent', data);
  };


  $scope.runParagraph = function(data) {
    //console.log('send new paragraph: %o with %o', $scope.paragraph.id, data);
    var parapgraphData = {op: 'RUN_PARAGRAPH', 
                          data: {
                              id: $scope.paragraph.id, 
                              title: $scope.paragraph.title,
                              paragraph: data, 
                              config: $scope.paragraph.config, 
                              params: $scope.paragraph.settings.params
                          }
                         };
    $rootScope.$emit('sendNewEvent', parapgraphData);
  };

  var flushTableData = function() {
    if ($scope.paragraph.settings.params._table) {
        delete $scope.paragraph.settings.params._table;
        if ($scope.paragraph.result) {
          delete $scope.paragraph.result;
        }
      }
  };

  $scope.moveUp = function() {
    $rootScope.$emit('moveParagraphUp', $scope.paragraph.id)
  };

  $scope.moveDown = function() {
    $rootScope.$emit('moveParagraphDown', $scope.paragraph.id)
  };

  $scope.removeParagraph = function() {
    var result = confirm('Do you want to delete this paragraph?');
    if (result) {
      console.log("Remove paragraph");
      var parapgraphData = {op: 'PARAGRAPH_REMOVE', data: {id: $scope.paragraph.id}};
      $rootScope.$emit('sendNewEvent', parapgraphData);
    }
  };

  $scope.closeParagraph = function() {
    console.log('close the note');
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.hide = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.openParagraph = function() {
    console.log('open the note');
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.hide = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.closeEditor = function() {
    console.log('close the note');

    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.editorHide = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.openEditor = function() {
    console.log('open the note');

    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.editorHide = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.showTitle = function() {
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.title = true;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);    
  };

  $scope.hideTitle = function() {
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    newConfig.title = false;

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);    
  };

  $scope.setTitle = function() {
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.changeColWidth = function() {

    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  $scope.loadForm = function(formulaire, params) {
    var value = formulaire.defaultValue;
    if (params[formulaire.name]) {
      value = params[formulaire.name];
    }
    $scope.forms[formulaire.name] = value;
  };

  $scope.aceChanged = function() {
    $scope.dirtyText = $scope.editor.getSession().getValue();
  };

  $scope.aceLoaded = function(_editor) {
    var langTools = ace.require("ace/ext/language_tools");
    var Range = ace.require('ace/range').Range

    $scope.editor = _editor;
    if (_editor.container.id !== '{{paragraph.id}}_editor') {
      $scope.editor.renderer.setShowGutter(false);
      $scope.editor.setHighlightActiveLine(false);
      $scope.editor.focus();
      var hight = $scope.editor.getSession().getScreenLength() * $scope.editor.renderer.lineHeight + $scope.editor.renderer.scrollBar.getWidth();
      setEditorHeight(_editor.container.id, hight);

      $scope.editor.getSession().setUseWrapMode(true);
      if (navigator.appVersion.indexOf("Mac")!=-1 ) {
        $scope.editor.setKeyboardHandler("ace/keyboard/emacs");
      } else if (navigator.appVersion.indexOf("Win")!=-1 ||
                 navigator.appVersion.indexOf("X11")!=-1 ||
                 navigator.appVersion.indexOf("Linux")!=-1) {
        // not applying emacs key binding while the binding override Ctrl-v. default behavior of paste text on windows.
      }

      $scope.editor.setOptions({
          enableBasicAutocompletion: true,
          enableSnippets: false,
          enableLiveAutocompletion:false
      });
      var remoteCompleter = {
          getCompletions : function(editor, session, pos, prefix, callback) {
              if (!$scope.editor.isFocused() ) return;

              var buf = session.getTextRange(new Range(0, 0, pos.row, pos.column))
              $rootScope.$emit('sendNewEvent', {
                  op : 'COMPLETION',
                  data : {
                      id : $scope.paragraph.id,
                      buf : buf,
                      cursor : buf.length
                  }
              });

              $rootScope.$on('completionList', function(event, data) {
                  if (data.completions) {
                      var completions = [];
                      for(var c in data.completions){
                          var v = data.completions[c]
                          completions.push({
                              name:v,
                              value:v,
                              score:300
                          });
                      }
                      callback(null, completions);
                  }
              });
          }
      }
      langTools.addCompleter(remoteCompleter);

      $scope.editor.getSession().on('change', function(e, editSession) {
        hight = editSession.getScreenLength() * $scope.editor.renderer.lineHeight + $scope.editor.renderer.scrollBar.getWidth();
        setEditorHeight(_editor.container.id, hight);
        $scope.editor.resize();
      });

      var code = $scope.editor.getSession().getValue();
      if ( String(code).startsWith('%sql')) {
        $scope.editor.getSession().setMode(editorMode.sql);
      } else if ( String(code).startsWith('%md')) {
        $scope.editor.getSession().setMode(editorMode.markdown);
      } else {
        $scope.editor.getSession().setMode(editorMode.scala);
      }

      $scope.editor.commands.addCommand({
        name: 'run',
        bindKey: {win: 'Shift-Enter', mac: 'Shift-Enter'},
        exec: function(editor) {
          var editorValue = editor.getValue();
          if (editorValue) {
            $scope.runParagraph(editorValue);
          }
        },
        readOnly: false
      });

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

      // autocomplete on 'ctrl+.'
      $scope.editor.commands.bindKey("ctrl-.", "startAutocomplete")
      $scope.editor.commands.bindKey("ctrl-space", null)

      // handle cursor moves
      $scope.editor.keyBinding.origOnCommandKey = $scope.editor.keyBinding.onCommandKey;
      $scope.editor.keyBinding.onCommandKey = function(e, hashId, keyCode) {
        if($scope.editor.completer && $scope.editor.completer.activated) { // if autocompleter is active
        } else {
            if(keyCode==38 || (keyCode==80 && e.ctrlKey)){  // UP
                var numRows = $scope.editor.getSession().getLength();
                var currentRow = $scope.editor.getCursorPosition().row
                if(currentRow==0){
                    // move focus to previous paragraph
                    $rootScope.$emit('moveFocusToPreviousParagraph', $scope.paragraph.id);
                }
            } else if(keyCode==40 || (keyCode==78 && e.ctrlKey)){  // DOWN
                var numRows = $scope.editor.getSession().getLength();
                var currentRow = $scope.editor.getCursorPosition().row
                if(currentRow == numRows-1){
                    // move focus to next paragraph
                    $rootScope.$emit('moveFocusToNextParagraph', $scope.paragraph.id);
                }
            }
        }
        this.origOnCommandKey(e, hashId, keyCode);
      }
    }
  };

  var setEditorHeight = function(id, height) {
    $('#' + id).height(height.toString() + 'px');
  };

  $scope.getEditorValue = function() {
    return $scope.editor.getValue();
  };

  $scope.getProgress = function(){
    return ($scope.currentProgress) ? $scope.currentProgress : 0;
  };

  $rootScope.$on('updateProgress', function(event, data) {
    if (data.id === $scope.paragraph.id) {
      $scope.currentProgress = data.progress
    }
  });

  $rootScope.$on('focusParagraph', function(event, paragraphId){
    if ($scope.paragraph.id === paragraphId) {
      $scope.editor.focus();
      $('body').scrollTo('#'+paragraphId+"_editor", 300, {offset:-60});
    }
  });

  $rootScope.$on('runParagraph', function(event){
    $scope.runParagraph($scope.editor.getValue());
  });

  $rootScope.$on('openEditor', function(event){
    $scope.openEditor();
  });

  $rootScope.$on('closeEditor', function(event){
    $scope.closeEditor();
  });


  $scope.getResultType = function(paragraph){
    var pdata = (paragraph) ? paragraph : $scope.paragraph;
    if (pdata.result && pdata.result.type) {
      return pdata.result.type;
    } else {
      return "TEXT";
    }
  };

  $scope.getBase64ImageSrc = function(base64Data) {
    return "data:image/png;base64,"+base64Data;
  };

  $scope.getGraphMode = function(paragraph){
    var pdata = (paragraph) ? paragraph : $scope.paragraph;
    if (pdata.settings.params && pdata.settings.params._table && pdata.settings.params._table.mode) {
      return pdata.settings.params._table.mode;
    } else {
      return "table";
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
      result.comment = "";
      var comment = false;

      for (var i = 0; i < textRows.length; i++) {
        var textRow = textRows[i];
        if (comment) {
          result.comment += textRow;
          continue;
        }

        if (textRow === '') {
          if (rows.length>0) {
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
            columnNames.push(col);
          } else {
            cols.push(col);
            cols2.push({key: columnNames[i], value: col});
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
      if (!type || type === 'table') {
        setTable($scope.paragraph.result, refresh);
      }
      else if (type === 'multiBarChart') {
        setD3Chart(type, $scope.paragraph.result, refresh);
      }
      else if (type === 'pieChart') {
        setD3Chart(type, $scope.paragraph.result, refresh);
      }
      else if (type === 'stackedAreaChart') {
        setD3Chart(type, $scope.paragraph.result, refresh);
      }
      else if (type === 'lineChart') {
        setD3Chart(type, $scope.paragraph.result, refresh);
      }
    }
  };

  var setNewMode = function(newMode) {
    var newConfig = jQuery.extend(true, {}, $scope.paragraph.config);
    var newParams = jQuery.extend(true, {}, $scope.paragraph.settings.params);
    newParams._table = {mode: newMode, height: 300.0};

    commitParagraph($scope.paragraph.title, $scope.paragraph.text, newConfig, newParams);
  };

  var commitParagraph = function(title, text, config, params) {
    var parapgraphData = {
      op: 'COMMIT_PARAGRAPH',
      data: {
        id: $scope.paragraph.id,
        title : title,
        paragraph: text,
        params: params,
        config: config
      }};
    $rootScope.$emit('sendNewEvent', parapgraphData);
  };

  var setTable = function(type, data, refresh) {

    var getTableContentFormat = function(d) {
      if (isNaN(d)) {
        if(d.length>"%html".length && "%html "===d.substring(0, "%html ".length)) {
          return "html";
        } else {
          return "";
        }
      } else {
        return "";
      }
    };

    var formatTableContent = function(d) {
      if (isNaN(d)) {
        var f = getTableContentFormat(d);
        if(f !="") {
          return d.substring(f.length+2);
        } else {
          return d;
        }
      } else {
        return d;
      }
    };


    var renderTable = function(){
      var html = "";
      html += '<table class="table table-hover table-condensed">';
      html += '  <thead>'
      html += '    <tr style="background-color: #EFEFEF; font-weight: bold;">';
      for (var c in $scope.paragraph.result.columnNames) {
        html += "<th>"+$scope.paragraph.result.columnNames[c]+"</th>";
      }
      html += '    </tr>';
      html += '  </thead>';

      for (var r in $scope.paragraph.result.msgTable) {
        var row = $scope.paragraph.result.msgTable[r];
        html += '    <tr>';
        for (var c in row) {
          var v = row[c].value;
          if(getTableContentFormat(v)!=="html") {
            v = v.replace(/[\u00A0-\u9999<>\&]/gim, function(i) {
                return '&#'+i.charCodeAt(0)+';';
            });
          }
          html += '      <td>'+formatTableContent(v)+'</td>';
        }
        html += '    </tr>';
      }


      html += '</table>';

      $('#p'+$scope.paragraph.id+"_table").html(html);
    };

    var retryRenderer = function(){
      if($('#p'+$scope.paragraph.id+"_table").length){
        try {
          renderTable();
        } catch(err) {
          console.log("Chart drawing error %o", err);
        }
      } else {
        $timeout(retryRenderer,10);
      }
    };
    $timeout(retryRenderer);

  };

  var setD3Chart = function(type, data, refresh) {
    if (!$scope.chart[type]) {
      var chart = nv.models[type]();
      $scope.chart[type] = chart;
    }

    var xColIndex = 0;
    var yColIndexes = [];
    var d3g = [];
    // select yColumns.

    if (type=="pieChart") {
      $scope.chart[type].x(function(d){ return d.label;})
                        .y(function(d){ return d.value;});

      for (var i = 0; i < data.columnNames.length; i++) {
        if (i !== xColIndex) {
          yColIndexes.push(i);
        }
      }

      for (var i = 0; i < data.rows.length; i++) {
        var row = data.rows[i];
        var xVar = row[xColIndex];
        var yVar = row[yColIndexes[0]];

        d3g.push({
            label: isNaN(xVar) ? xVar : parseFloat(xVar),
            value: parseFloat(yVar)
        });
      }
    } else {
      $scope.chart[type].yAxis.axisLabelDistance(50);

      for (var i = 0; i < data.columnNames.length; i++) {
        if (i !== xColIndex) {
          yColIndexes.push(i);
          d3g.push({
            values: [],
            key: data.columnNames[i]
          });
        }
      }

      var xLabels = {};

      var xValue = function(x,i) {
        if (isNaN(x)) {
          if (type==="multiBarChart" || type==="pieChart") {
            return x;
          } else {
            xLabels[i] = x;
            return i;
          }
        } else {
          return parseFloat(x);           
        }
      };

      var yValue = function(y) {
        if (isNaN(y)) {
          return 0; 
        } else {
          return parseFloat(y);
        }
      }

      for (i = 0; i < data.rows.length; i++) {
        var row = data.rows[i];
        for (var j = 0; j < yColIndexes.length; j++) {
          var xVar = row[xColIndex];
          var yVar = row[yColIndexes[j]];
          d3g[j].values.push({
            x: xValue(xVar, i),
            y: yValue(yVar)
          });
        }
      }

      $scope.chart[type].xAxis.tickFormat(function(d) {
        if (xLabels[d] ) {
          return xLabels[d]
        } else {
          return d;
        }
      });


    }

    var renderChart = function(){
      if (!refresh) {
        // TODO force destroy previous chart
      }

      var height = $scope.paragraph.settings.params._table.height;

      var chartEl = d3.select("#p"+$scope.paragraph.id+"_"+type+" svg")
          .attr('height', $scope.paragraph.settings.params._table.height)
          .datum(d3g) 
          .transition()
          .duration(300)
          .call($scope.chart[type]);
      d3.select("#p"+$scope.paragraph.id+"_"+type+" svg").style.height = height+"px";
      nv.utils.windowResize($scope.chart[type].update);
    };

    var retryRenderer = function(){
      if($('#p'+$scope.paragraph.id+"_"+type+" svg").length!==0){
        try {
          renderChart();
        } catch(err) {
          console.log("Chart drawing error %o", err);
        }
      } else {
        $timeout(retryRenderer,10);
      }
    };
    $timeout(retryRenderer);
  };

  var setPieChart = function(data, refresh) {
    var xColIndex = 0;
    var yColIndexes = [];
    var d3g = [];

    // select yColumns.
    for (var i = 0; i < data.columnNames.length; i++) {
      if (i !== xColIndex) {
        yColIndexes.push(i);
      }
    }

    for (var i = 0; i < data.rows.length; i++) {
      var row = data.rows[i];
      var xVar = row[xColIndex];
      var yVar = row[yColIndexes[0]];

      d3g.push({
          label: isNaN(xVar) ? xVar : parseFloat(xVar),
          value: parseFloat(yVar)
      });
    }

    if ($scope.d3.pieChart.data === null || !refresh) {
      $scope.d3.pieChart.data = d3g;
      $scope.d3.pieChart.options.chart.height = $scope.paragraph.settings.params._table.height;

      if ($scope.d3.pieChart.api) {
        $scope.d3.pieChart.api.updateWithOptions($scope.d3.pieChart.options);
      }
    } else {
      if ($scope.d3.pieChart.api) {
        $scope.d3.pieChart.api.updateWithData(d3g);
      }
    }
  };


  $scope.isGraphMode = function(graphName) {
    if ($scope.getResultType() === "TABLE" && $scope.getGraphMode()===graphName) {
      return true;
    } else {
      return false;
    }
  };

  /** Utility function */
  if (typeof String.prototype.startsWith !== 'function') {
    String.prototype.startsWith = function(str) {
      return this.slice(0, str.length) === str;
    };
  }

  $scope.goToSingleParagraph = function () {
    var noteId = $route.current.pathParams.noteId;
    var redirectToUrl = location.protocol + '//' + location.host + '/#/notebook/' + noteId + "/paragraph/" + $scope.paragraph.id+"?asIframe";
    $window.open(redirectToUrl);
  };

});
