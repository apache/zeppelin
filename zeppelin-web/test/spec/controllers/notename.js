'use strict';

describe('Controller: NotenameCtrl', function() {
  beforeEach(module('zeppelinWebApp'));

  var scope;
  var ctrl;
  var notebookList;

  beforeEach(inject(function($injector) {
    notebookList = $injector.get('notebookListDataFactory');
  }));

  beforeEach(inject(function($rootScope, $controller) {
    scope = $rootScope.$new();
    ctrl = $controller('NotenameCtrl', {
      $scope: scope,
      notebookListDataFactory: notebookList
    });
  }));

  it('should create a new name from current name when cloneNoteName is called', function() {
    var notesList = [
      {name: 'Copy of dsds 1', id: '1'},
      {name: 'Copy of dsds 2', id: '2'},
      {name: 'test name', id: '3'},
      {name: 'aa bb cc', id: '4'},
      {name: 'Untitled Note 6', id: '4'}
    ];

    notebookList.setNotes(notesList);

    ctrl.sourceNoteName = 'test name';
    expect(ctrl.cloneNoteName()).toEqual('Copy of test name 1');
    ctrl.sourceNoteName = 'aa bb cc';
    expect(ctrl.cloneNoteName()).toEqual('Copy of aa bb cc 1');
    ctrl.sourceNoteName = 'Untitled Note 6';
    expect(ctrl.cloneNoteName()).toEqual('Copy of Untitled Note 6 1');
    ctrl.sourceNoteName = 'My_note';
    expect(ctrl.cloneNoteName()).toEqual('Copy of My_note 1');
    ctrl.sourceNoteName = 'Copy of dsds 2';
    expect(ctrl.cloneNoteName()).toEqual('Copy of dsds 3');
  });

});
