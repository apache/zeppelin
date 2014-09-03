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
    
    if ($scope.paragraph.settings.params['_table'] && $scope.paragraph.result) {
      console.log('init %o', newParagraph);
      $scope.loadResultType($scope.paragraph.result);
      $scope.setMode($scope.paragraph.settings.params['_table'].mode, false);
    }
  };

  $scope.$on('updateParagraph', function(event, data) {
    if (data.paragraph.id === $scope.paragraph.id) {
      console.log('>>>>>>> %o = %o',data.paragraph,  $scope.paragraph);
      //debugger;
      $scope.paragraph = data.paragraph;
      $scope.loadResultType($scope.paragraph.result);
      $scope.setMode($scope.paragraph.settings.params['_table'].mode, false);
    }
  });
  
  
  
  $scope.sendParagraph = function(data) {
    //TODO: check if contnet changed
    console.log('send new paragraph: %o with %o', $scope.paragraph.id, data);
    var parapgraphData = {op: 'RUN_PARAGRAPH', data: {id: $scope.paragraph.id, paragraph: data, params: $scope.paragraph.settings.params}};
    
    $scope.$emit('sendNewData', parapgraphData);
  };

  var updateParagraph = function(data) {
    $scope.paragraph = data;
    
  };
  
  $scope.closeParagraph = function() {
    console.log('close the note');
    var parapgraphData = {op: 'PARAGRAPH_UPDATE_STATE', data: {id: $scope.paragraph.id, isClose: true, isEditorClose: !$scope.paragraph.isEditorOpen}};
    $scope.$emit('sendNewData', parapgraphData);
  };
  
  $scope.removeParagraph = function() {
    console.log('remove the note');
    var parapgraphData = {op: 'PARAGRAPH_REMOVE', data: {id: $scope.paragraph.id}};
    $scope.$emit('sendNewData', parapgraphData);
  };
  
  $scope.openParagraph = function() {
    console.log('open the note');
    var parapgraphData = {op: 'PARAGRAPH_UPDATE_STATE', data: {id: $scope.paragraph.id, isClose: false, isEditorClose: !$scope.paragraph.isEditorOpen}};
    $scope.$emit('sendNewData', parapgraphData);
  };
  
  $scope.closeEditor = function() {
    console.log('close the note');
    var parapgraphData = {op: 'PARAGRAPH_UPDATE_STATE', data: {id: $scope.paragraph.id, isClose: !$scope.paragraph.isOpen, isEditorClose: true}};
    $scope.$emit('sendNewData', parapgraphData);
  };
  
  $scope.openEditor = function() {
    console.log('open the note');
    var parapgraphData = {op: 'PARAGRAPH_UPDATE_STATE', data: {id: $scope.paragraph.id, isClose: !$scope.paragraph.isOpen, isEditorClose: false}};
    $scope.$emit('sendNewData', parapgraphData);
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
      result.msgTable = array;
      result.columnNames = columnNames;
      result.rows = rows;
      result.mode = 'TABLE';
    }
  };

// todo: Change the name
  $scope.setMode = function(type, emit) {
    if (emit) {
      setNewMode(type);
    } else {
      if (!type || type === 'table') {
      }
      else if (type === 'multiBarChart') {
        setMultiBarChart($scope.paragraph.result);
      }
      else if (type === 'pieChart') {
      }
      else if (type === 'stackedAreaChart') {
        setStackedAreaChart($scope.paragraph.result);
      }
      else if (type === 'lineChart') {
        setLineChart($scope.paragraph.result);
      }
    }
  };

  var setD3Configuration = function() {
    $scope.d3.config.visible = true;
    $scope.d3.config.autorefresh = true;
    $scope.d3.config.disabled = false;
    $scope.d3.refreshDataOnly = true;
  };

  var unSetD3Configuration = function() {
    $scope.d3.config.visible = false;
    $scope.d3.config.autorefresh = false;
    $scope.d3.config.disabled = true;
  };

  var setNewMode = function(newMode) {
    $scope.paragraph.settings.params['_table'] = {mode: newMode, height: 300.0};
    var parapgraphData = {
      op: "COMMIT_PARAGRAPH",
      data: {
        id: $scope.paragraph.id,
        paragraph: $scope.paragraph.text,
        params: $scope.paragraph.settings.params
      }};
    $scope.$emit('sendNewData', parapgraphData);
  };

  var setMultiBarChart = function(data) {
    setD3Configuration();
    $scope.d3.options = {
      chart: {
        type: 'multiBarChart',
        height: $scope.paragraph.settings.params['_table'].height,
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
    $scope.d3.data = newData;
  };

  var setLineChart = function(data) {
    setD3Configuration();
    $scope.d3.options = {
      chart: {
        type: 'lineChart',
        height: $scope.paragraph.settings.params['_table'].height,
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
    $scope.d3.data = newData;
    
    console.log('OUI >>> %', $scope.d3.data);
  };

  var setStackedAreaChart = function(data) {
    setD3Configuration();
    $scope.d3.options = {
      chart: {
        type: 'stackedAreaChart',
        height: $scope.paragraph.settings.params['_table'].height,
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
    $scope.d3.data = newData;
    
    console.log('OUI >>> %', $scope.d3.data);
  };

  $scope.isTable = function() {
    if ($scope.paragraph.result) {
      if ($scope.paragraph.result.type === 'TABLE'
         && (!$scope.paragraph.settings.params['_table'] || $scope.paragraph.settings.params['_table'].mode === 'table')) {
        return true;
      }
    }
    return false;
  };
  
  $scope.isGraphActive = function(graphName) {
    if ($scope.paragraph.result && $scope.paragraph.settings.params['_table']) {
      if ($scope.paragraph.settings.params['_table'].mode === graphName) {
        return true;
      }
    }
    return false;
  };

  

});
