'use strict';

/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:ParagraphCtrl
 * @description
 * # ParagraphCtrl
 * Controller of the zeppelinWeb2App
 */
angular.module('zeppelinWeb2App')
        .controller('ParagraphCtrl', function($scope) {

  $scope.paragraph = null;
  $scope.editor = null;
  var editorMode = {scala: 'ace/mode/scala'};
  $scope.graphMode = '';

  $scope.forms = {};

  $scope.d3 = {};
  $scope.d3.options = {
      chart: {
        type: 'multiBarChart',
        height: 450,
        margin: {
          top: 20,
          right: 20,
          bottom: 60,
          left: 45
        },
        clipEdge: true,
        staggerLabels: true,
        transitionDuration: 500
      }
    };
  $scope.d3.data = [];
  $scope.d3.config = {
    visible: false, // default: true
    extended: false, // default: false
    disabled: false, // default: false
    autorefresh: true, // default: true
    refreshDataOnly: false // default: false
  };

  // Controller init
  $scope.init = function(newParagraph) {
    $scope.paragraph = newParagraph;
    if ($scope.updateParagrapheInformation === []) {
      return ;
    }
    if ($scope.updateParagrapheInformation[$scope.paragraph.id]) {
      $scope.graphMode = $scope.updateParagrapheInformation[$scope.paragraph.id];
    }
  };

  $scope.$on('updateParagraph', function(event, data) {
    console.log('%%%%%%%% %o', data);
  });
  
  $scope.$on('updatedInformation', function(event, data) {
    if (data.id === $scope.paragraph.id) {
      if (!event.defaultPrevented) {
        $scope.graphMode = data.graphMode;
        event.preventDefault();
      }
    }
  });


  $scope.sendParagraph = function(data) {
    //TODO: check if contnet changed
    console.log('send new paragraph: %o with %o', $scope.paragraph.id, data);
    var parapgraphData = {op: 'RUN_PARAGRAPH', data: {id: $scope.paragraph.id, paragraph: data, params: $scope.forms}};
    var info = {id: $scope.paragraph.id, graphMode: $scope.graphMode};
    $scope.$emit('sendNewData', parapgraphData, info);
  };

  var updateParagraph = function(data) {
    $scope.paragraph = data;
  };


  $scope.loardForm = function(formulaire, params) {
    var value = formulaire.defaultValue;
    if (params[formulaire.name]) {
      value = params[formulaire.name];
    }
    $scope.forms[formulaire.name] = value;
  };

  $scope.aceLoaded = function(_editor) {
    $scope.editor = _editor;
    if (_editor.container.id !== '{{paragraph.id}}_editor') {

      $scope.editor.renderer.setShowGutter(false);
      $scope.editor.setHighlightActiveLine(false);
      var hight = $scope.editor.getSession().getScreenLength() * $scope.editor.renderer.lineHeight + $scope.editor.renderer.scrollBar.getWidth();
      setEditorHeight(_editor.container.id, hight);
      $scope.editor.getSession().on('change', function(e, editSession) {
        hight = editSession.getScreenLength() * $scope.editor.renderer.lineHeight + $scope.editor.renderer.scrollBar.getWidth();
        setEditorHeight(_editor.container.id, hight);
        $scope.editor.resize();
      });
      $scope.editor.getSession().setMode(editorMode.scala);
      $scope.editor.commands.addCommand({
        name: 'run',
        bindKey: {win: 'Shift-Enter', mac: 'Shift-Enter'},
        exec: function(editor) {
          var editorValue = editor.getValue();
          if (editorValue) {
            $scope.sendParagraph(editorValue);
          }
        },
        readOnly: false
      });
    }
  };

  var getParagraphFromAceId = function(aceId) {
    var position = aceId.indexOf('_editor');
    return aceId.substr(0, position);
  };

  var setEditorHeight = function(id, height) {
    $('#' + id).height(height.toString() + 'px');
  };

  $scope.getEditorValue = function() {
    return $scope.editor.getValue();
  };


  $scope.loadResultType = function(result) {
    if (!result) {
      return;
    }
    if (result.type === 'TABLE') {
      var columnNames = [];
      var rows = [];
      var array = [];
      var textRows = result.msg.split('\n');

      for (var i = 0; i < textRows.length; i++) {
        var textRow = textRows[i];
        if (textRow === '') {
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
      result.msgSave = result.msg;
      result.msg = array;
      result.columnNames = columnNames;
      result.rows = rows;
      result.mode = 'TABLE';
      if ($scope.graphMode === '') {
        $scope.graphMode = 'TABLE';
      }
      getGraphMode(result);
    }
  };

  $scope.setMode = function(type, data) {
    if (type === 'TABLE') {
      setDefaultTable();
    }
    if (type === 'multiBarChart') {
      setMultiBarChart(data);
    }
    else if (type === 'pieChart') {
      $scope.d3.options.chart.type = 'pieChart';
    }
    else if (type === 'stackedAreaChart') {
      $scope.d3.options.chart.type = 'stackedAreaChart';
    }
    else if (type === 'lineChart') {
    }
  };

  var getGraphMode = function(data) {
    $scope.setMode($scope.graphMode, data);
  };
  
  var setDefaultTable = function() {
    $scope.graphMode = 'TABLE';
  };


  var setD3Configuration = function() {
    $scope.d3.config.visible = true;
    $scope.d3.config.autorefresh = false;
    $scope.d3.config.disabled = false;
    $scope.d3.refreshDataOnly = true;
  };

  var unSetD3Configuration = function() {
    $scope.d3.config.visible = false;
    $scope.d3.config.autorefresh = false;
    $scope.d3.config.disabled = true;
  };

  var setMultiBarChart = function(data) {
    $scope.graphMode = 'multiBarChart';
    setD3Configuration();
    $scope.d3.options = {
      chart: {
        type: 'multiBarChart',
        height: 450,
        margin: {
          top: 20,
          right: 20,
          bottom: 60,
          left: 45
        },
        clipEdge: true,
        staggerLabels: true,
        transitionDuration: 500
      }
    };

    var xColIndex = 0;
    var yColIndexes = [];
    var d3g = [];
    // select yColumns. 
    for (var i = 0; i < data.columnNames.length; i++) {
      if (i !== xColIndex) {
        yColIndexes.push(i);
        d3g.push({
          values: [],
          key: data.columnNames[i]
        });
      }
    }

    for (i = 0; i < data.rows.length; i++) {
      var row = data.rows[i];
      for (var j = 0; j < yColIndexes.length; j++) {
        var xVar = row[xColIndex];
        var yVar = row[yColIndexes[j]];
        d3g[j].values.push({
          x: isNaN(xVar) ? xVar : parseFloat(xVar),
          y: parseFloat(yVar)
        });
      }
    }

    var newData = d3g;
    //$scope.d3.data = newData;
    console.log('data %o --- % o', $scope.d3.data, newData);
    $scope.d3.data = newData;
  };

});
