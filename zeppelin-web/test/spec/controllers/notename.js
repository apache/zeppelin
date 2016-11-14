'use strict';

describe('Controller: NotenameCtrl', function() {
  beforeEach(module('zeppelinWebApp'));

  var scope;
  var ctrl;
  var noteList;

  beforeEach(inject(function($injector, $rootScope, $controller) {
    noteList = $injector.get('noteListDataFactory');
    scope = $rootScope.$new();
    ctrl = $controller('NotenameCtrl', {
      $scope: scope,
      noteListDataFactory: noteList
    });
  }));

  it('should create a new name from current name when cloneNoteName is called', function() {
    var notesList = [
      {name: 'dsds 1', id: '1'},
      {name: 'dsds 2', id: '2'},
      {name: 'test name', id: '3'},
      {name: 'aa bb cc', id: '4'},
      {name: 'Untitled Note 6', id: '4'}
    ];

    noteList.setNotes(notesList);

    ctrl.sourceNoteName = 'test name';
    expect(ctrl.cloneNoteName()).toEqual('test name 1');
    ctrl.sourceNoteName = 'aa bb cc';
    expect(ctrl.cloneNoteName()).toEqual('aa bb cc 1');
    ctrl.sourceNoteName = 'Untitled Note 6';
    expect(ctrl.cloneNoteName()).toEqual('Untitled Note 7');
    ctrl.sourceNoteName = 'My_note';
    expect(ctrl.cloneNoteName()).toEqual('My_note 1');
    ctrl.sourceNoteName = 'dsds 2';
    expect(ctrl.cloneNoteName()).toEqual('dsds 3');
  });

});
