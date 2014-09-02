'use strict';

/**
 * @ngdoc function
 * @name zeppelinWeb2App.controller:NotebookCtrl
 * @description
 * # NotebookCtrl
 * Controller of the zeppelinWeb2App
 */
angular.module('zeppelinWeb2App')
        .controller('NotebookCtrl', function($scope, WebSocket) {

  // Controller init
  $scope.init = function() {
    getAllNotes();
  };

  $scope.updateParagrapheInformation = [];

  // Native Functions
  WebSocket.onopen(function() {
    console.log('Websocket created');
    getAllNotes();
  });

  WebSocket.onmessage(function(event) {
    var payload;
    if (event.data) {
      payload = angular.fromJson(event.data);
    }
    console.log('Receive << %o, %o, %o', payload.op, payload);
    var op = payload.op;
    var data = payload.data;
    if (op === 'NOTE') {
      $scope.note = data.note;
      //$scope.$broadcast('updatedInformation', updateParagrapheInformation);
    } else if (op === 'NOTES_INFO') {
      /** if all note are removed, empty the crap */
      if (data.notes.length === 0) {
        $scope.note = [];
      }
      $scope.noteInfo = data.notes;
    } else if (op === 'PARAGRAPH') {
      // TODO send Event
      //$scope.$broadcast('updateParagraph', data.paragraph);
      //updateParagraph(data.paragraph);
    }
  });

  WebSocket.onerror(function(event) {
    console.log('message: ', event.data);
  });

  WebSocket.onclose(function(event) {
    console.log('message: ', event.data);
  });

  $scope.createNote = function() {
    send({op: 'NEW_NOTE'});
  };

  $scope.getNote = function(noteId) {
    send({op: 'GET_NOTE', data: {id: noteId}});
  };

  $scope.removeNote = function(noteId) {
    send({op: 'DEL_NOTE', data: {id: noteId}});
  };

  var getAllNotes = function() {
    send({op: 'LIST_NOTES'});
  };

  var send = function(data) {
    console.log('Send >> %o, %o', data.op, data);
    WebSocket.send(JSON.stringify(data));
  };

  $scope.$on('sendNewData', function(event, data, info) {
    if (!event.defaultPrevented) {
      $scope.updateParagrapheInformation[info.id] = info.graphMode;
      send(data);
      event.preventDefault();
    }
  });
});
