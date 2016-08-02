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

angular.module('zeppelinWebApp').controller('NotebookCtrl', function($scope, $route, $routeParams, $location,
                                                                     $rootScope, $http, websocketMsgSrv,
                                                                     baseUrlSrv, $timeout, saveAsService) {
  $scope.note = null;
  $scope.moment = moment;
  $scope.showEditor = false;
  $scope.editorToggled = false;
  $scope.tableToggled = false;
  $scope.viewOnly = false;
  $scope.showSetting = false;
  $scope.looknfeelOption = ['default', 'simple', 'report'];
  $scope.cronOption = [
    {name: 'None', value: undefined},
    {name: '1m', value: '0 0/1 * * * ?'},
    {name: '5m', value: '0 0/5 * * * ?'},
    {name: '1h', value: '0 0 0/1 * * ?'},
    {name: '3h', value: '0 0 0/3 * * ?'},
    {name: '6h', value: '0 0 0/6 * * ?'},
    {name: '12h', value: '0 0 0/12 * * ?'},
    {name: '1d', value: '0 0 0 * * ?'}
  ];

  $scope.interpreterSettings = [];
  $scope.interpreterBindings = [];
  $scope.isNoteDirty = null;
  $scope.saveTimer = null;

  var connectedOnce = false;

  // user auto complete related
  $scope.suggestions = [];
  $scope.selectIndex = -1;
  var selectedUser = '';
  var selectedUserIndex = 0;
  var previousSelectedList = [];
  var previousSelectedListOwners = [];
  var previousSelectedListReaders = [];
  var previousSelectedListWriters = [];
  var searchText = [];
  $scope.role = '';
  $scope.noteRevisions = [];

  $scope.$on('setConnectedStatus', function(event, param) {
    if (connectedOnce && param) {
      initNotebook();
    }
    connectedOnce = true;
  });

  $scope.getCronOptionNameFromValue = function(value) {
    if (!value) {
      return '';
    }

    for (var o in $scope.cronOption) {
      if ($scope.cronOption[o].value === value) {
        return $scope.cronOption[o].name;
      }
    }
    return value;
  };

  /** Init the new controller */
  var initNotebook = function() {
    websocketMsgSrv.getNotebook($routeParams.noteId);
    websocketMsgSrv.listRevisionHistory($routeParams.noteId);

    var currentRoute = $route.current;
    if (currentRoute) {
      setTimeout(
        function() {
          var routeParams = currentRoute.params;
          var $id = angular.element('#' + routeParams.paragraph + '_container');

          if ($id.length > 0) {
            // adjust for navbar
            var top = $id.offset().top - 103;
            angular.element('html, body').scrollTo({top: top, left: 0});
          }

          // force notebook reload on user change
          $scope.$on('setNoteMenu', function(event, note) {
            initNotebook();
          });
        },
        1000
      );
    }
  };

  initNotebook();

  $scope.focusParagraphOnClick = function(clickEvent) {
    if (!$scope.note) {
      return;
    }
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      var paragraphId = $scope.note.paragraphs[i].id;
      if (jQuery.contains(angular.element('#' + paragraphId + '_container')[0], clickEvent.target)) {
        $scope.$broadcast('focusParagraph', paragraphId, 0, true);
        break;
      }
    }
  };

  // register mouseevent handler for focus paragraph
  document.addEventListener('click', $scope.focusParagraphOnClick);

  $scope.keyboardShortcut = function(keyEvent) {
    // handle keyevent
    if (!$scope.viewOnly) {
      $scope.$broadcast('keyEvent', keyEvent);
    }
  };

  // register mouseevent handler for focus paragraph
  document.addEventListener('keydown', $scope.keyboardShortcut);

  /** Remove the note and go back tot he main page */
  /** TODO(anthony): In the nearly future, go back to the main page and telle to the dude that the note have been remove */
  $scope.removeNote = function(noteId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to delete this notebook?',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.deleteNotebook(noteId);
          $location.path('/');
        }
      }
    });
  };

  //Export notebook
  $scope.exportNotebook = function() {
    var jsonContent = JSON.stringify($scope.note);
    saveAsService.saveAs(jsonContent, $scope.note.name, 'json');
  };

  //Clone note
  $scope.cloneNote = function(noteId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to clone this notebook?',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.cloneNotebook(noteId);
          $location.path('/');
        }
      }
    });
  };

  // checkpoint/commit notebook
  $scope.checkpointNotebook = function(commitMessage) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Commit notebook to current repository?',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.checkpointNotebook($routeParams.noteId, commitMessage);
        }
      }
    });
    document.getElementById('note.checkpoint.message').value = '';
  };

  $scope.$on('listRevisionHistory', function(event, data) {
    console.log('We got the revisions %o', data);
    $scope.noteRevisions = data.revisionList;
  });

  // receive certain revision of note
  $scope.$on('noteRevision', function(event, data) {
    console.log('received note revision %o', data);
    //TODO(xxx): render it
  });

  $scope.runNote = function() {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Run all paragraphs?',
      callback: function(result) {
        if (result) {
          _.forEach($scope.note.paragraphs, function(n, key) {
            angular.element('#' + n.id + '_paragraphColumn_main').scope().runParagraph(n.text);
          });
        }
      }
    });
  };

  $scope.saveNote = function() {
    if ($scope.note && $scope.note.paragraphs) {
      _.forEach($scope.note.paragraphs, function(n, key) {
        angular.element('#' + n.id + '_paragraphColumn_main').scope().saveParagraph();
      });
      $scope.isNoteDirty = null;
    }
  };

  $scope.clearAllParagraphOutput = function() {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to clear all output?',
      callback: function(result) {
        if (result) {
          _.forEach($scope.note.paragraphs, function(n, key) {
            angular.element('#' + n.id + '_paragraphColumn_main').scope().clearParagraphOutput();
          });
        }
      }
    });
  };

  $scope.toggleAllEditor = function() {
    if ($scope.editorToggled) {
      $scope.$broadcast('openEditor');
    } else {
      $scope.$broadcast('closeEditor');
    }
    $scope.editorToggled = !$scope.editorToggled;
  };

  $scope.showAllEditor = function() {
    $scope.$broadcast('openEditor');
  };

  $scope.hideAllEditor = function() {
    $scope.$broadcast('closeEditor');
  };

  $scope.toggleAllTable = function() {
    if ($scope.tableToggled) {
      $scope.$broadcast('openTable');
    } else {
      $scope.$broadcast('closeTable');
    }
    $scope.tableToggled = !$scope.tableToggled;
  };

  $scope.showAllTable = function() {
    $scope.$broadcast('openTable');
  };

  $scope.hideAllTable = function() {
    $scope.$broadcast('closeTable');
  };

  $scope.isNoteRunning = function() {
    var running = false;
    if (!$scope.note) { return false; }
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].status === 'PENDING' || $scope.note.paragraphs[i].status === 'RUNNING') {
        running = true;
        break;
      }
    }
    return running;
  };

  $scope.killSaveTimer = function() {
    if ($scope.saveTimer) {
      $timeout.cancel($scope.saveTimer);
      $scope.saveTimer = null;
    }
  };

  $scope.startSaveTimer = function() {
    $scope.killSaveTimer();
    $scope.isNoteDirty = true;
    //console.log('startSaveTimer called ' + $scope.note.id);
    $scope.saveTimer = $timeout(function() {
      $scope.saveNote();
    }, 10000);
  };

  angular.element(window).on('beforeunload', function(e) {
    $scope.killSaveTimer();
    $scope.saveNote();
  });

  $scope.setLookAndFeel = function(looknfeel) {
    $scope.note.config.looknfeel = looknfeel;
    $scope.setConfig();
  };

  /** Set cron expression for this note **/
  $scope.setCronScheduler = function(cronExpr) {
    $scope.note.config.cron = cronExpr;
    $scope.setConfig();
  };

  /** Set the username of the user to be used to execute all notes in notebook **/
  $scope.setCronExecutingUser = function(cronExecutingUser) {
    $scope.note.config.cronExecutingUser = cronExecutingUser;
    $scope.setConfig();
  };

  /** Set release resource for this note **/
  $scope.setReleaseResource = function(value) {
    $scope.note.config.releaseresource = value;
    $scope.setConfig();
  };

  /** Update note config **/
  $scope.setConfig = function(config) {
    if (config) {
      $scope.note.config = config;
    }
    websocketMsgSrv.updateNotebook($scope.note.id, $scope.note.name, $scope.note.config);
  };

  /** Update the note name */
  $scope.sendNewName = function() {
    if ($scope.note.name) {
      websocketMsgSrv.updateNotebook($scope.note.id, $scope.note.name, $scope.note.config);
    }
  };

  var initializeLookAndFeel = function() {
    if (!$scope.note.config.looknfeel) {
      $scope.note.config.looknfeel = 'default';
    } else {
      $scope.viewOnly = $scope.note.config.looknfeel === 'report' ? true : false;
    }
    $rootScope.$broadcast('setLookAndFeel', $scope.note.config.looknfeel);
  };

  var cleanParagraphExcept = function(paragraphId, note) {
    var noteCopy = {};
    noteCopy.id = note.id;
    noteCopy.name = note.name;
    noteCopy.config = note.config;
    noteCopy.info = note.info;
    noteCopy.paragraphs = [];
    for (var i = 0; i < note.paragraphs.length; i++) {
      if (note.paragraphs[i].id === paragraphId) {
        noteCopy.paragraphs[0] = note.paragraphs[i];
        if (!noteCopy.paragraphs[0].config) {
          noteCopy.paragraphs[0].config = {};
        }
        noteCopy.paragraphs[0].config.editorHide = true;
        noteCopy.paragraphs[0].config.tableHide = false;
        break;
      }
    }
    return noteCopy;
  };

  var updateNote = function(note) {
    /** update Note name */
    if (note.name !== $scope.note.name) {
      console.log('change note name: %o to %o', $scope.note.name, note.name);
      $scope.note.name = note.name;
    }

    $scope.note.config = note.config;
    $scope.note.info = note.info;

    var newParagraphIds = note.paragraphs.map(function(x) {return x.id;});
    var oldParagraphIds = $scope.note.paragraphs.map(function(x) {return x.id;});

    var numNewParagraphs = newParagraphIds.length;
    var numOldParagraphs = oldParagraphIds.length;

    var paragraphToBeFocused;
    var focusedParagraph;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      var paragraphId = $scope.note.paragraphs[i].id;
      if (angular.element('#' + paragraphId + '_paragraphColumn_main').scope().paragraphFocused) {
        focusedParagraph = paragraphId;
        break;
      }
    }

    /** add a new paragraph */
    if (numNewParagraphs > numOldParagraphs) {
      for (var index in newParagraphIds) {
        if (oldParagraphIds[index] !== newParagraphIds[index]) {
          $scope.note.paragraphs.splice(index, 0, note.paragraphs[index]);
          paragraphToBeFocused = note.paragraphs[index].id;
          break;
        }
        $scope.$broadcast('updateParagraph', {
          note: $scope.note, // pass the note object to paragraph scope
          paragraph: note.paragraphs[index]});
      }
    }

    /** update or move paragraph */
    if (numNewParagraphs === numOldParagraphs) {
      for (var idx in newParagraphIds) {
        var newEntry = note.paragraphs[idx];
        if (oldParagraphIds[idx] === newParagraphIds[idx]) {
          $scope.$broadcast('updateParagraph', {
            note: $scope.note, // pass the note object to paragraph scope
            paragraph: newEntry});
        } else {
          // move paragraph
          var oldIdx = oldParagraphIds.indexOf(newParagraphIds[idx]);
          $scope.note.paragraphs.splice(oldIdx, 1);
          $scope.note.paragraphs.splice(idx, 0, newEntry);
          // rebuild id list since paragraph has moved.
          oldParagraphIds = $scope.note.paragraphs.map(function(x) {return x.id;});
        }

        if (focusedParagraph === newParagraphIds[idx]) {
          paragraphToBeFocused = focusedParagraph;
        }
      }
    }

    /** remove paragraph */
    if (numNewParagraphs < numOldParagraphs) {
      for (var oldidx in oldParagraphIds) {
        if (oldParagraphIds[oldidx] !== newParagraphIds[oldidx]) {
          $scope.note.paragraphs.splice(oldidx, 1);
          break;
        }
      }
    }

    // restore focus of paragraph
    for (var f = 0; f < $scope.note.paragraphs.length; f++) {
      if (paragraphToBeFocused === $scope.note.paragraphs[f].id) {
        $scope.note.paragraphs[f].focus = true;
      }
    }
  };

  var getInterpreterBindings = function(callback) {
    $http.get(baseUrlSrv.getRestApiBase() + '/notebook/interpreter/bind/' + $scope.note.id).
    success(function(data, status, headers, config) {
      $scope.interpreterBindings = data.body;
      $scope.interpreterBindingsOrig = angular.copy($scope.interpreterBindings); // to check dirty
      if (callback) {
        callback();
      }
    }).
    error(function(data, status, headers, config) {
      if (status !== 0) {
        console.log('Error %o %o', status, data.message);
      }
    });
  };

  var getInterpreterBindingsCallBack = function() {
    var selected = false;
    var key;
    var setting;

    for (key in $scope.interpreterBindings) {
      setting = $scope.interpreterBindings[key];
      if (setting.selected) {
        selected = true;
        break;
      }
    }

    if (!selected) {
      // make default selection
      var selectedIntp = {};
      for (key in $scope.interpreterBindings) {
        setting = $scope.interpreterBindings[key];
        if (!selectedIntp[setting.name]) {
          setting.selected = true;
          selectedIntp[setting.name] = true;
        }
      }
      $scope.showSetting = true;
    }
  };

  $scope.interpreterSelectionListeners = {
    accept: function(sourceItemHandleScope, destSortableScope) {return true;},
    itemMoved: function(event) {},
    orderChanged: function(event) {}
  };

  $scope.openSetting = function() {
    $scope.showSetting = true;
    getInterpreterBindings();
  };

  $scope.closeSetting = function() {
    if (isSettingDirty()) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Interpreter setting changes will be discarded.',
        callback: function(result) {
          if (result) {
            $scope.$apply(function() {
              $scope.showSetting = false;
            });
          }
        }
      });
    } else {
      $scope.showSetting = false;
    }
  };

  $scope.saveSetting = function() {
    var selectedSettingIds = [];
    for (var no in $scope.interpreterBindings) {
      var setting = $scope.interpreterBindings[no];
      if (setting.selected) {
        selectedSettingIds.push(setting.id);
      }
    }

    $http.put(baseUrlSrv.getRestApiBase() + '/notebook/interpreter/bind/' + $scope.note.id,
              selectedSettingIds).
    success(function(data, status, headers, config) {
      console.log('Interpreter binding %o saved', selectedSettingIds);
      $scope.showSetting = false;
    }).
    error(function(data, status, headers, config) {
      console.log('Error %o %o', status, data.message);
    });
  };

  $scope.toggleSetting = function() {
    if ($scope.showSetting) {
      $scope.closeSetting();
    } else {
      $scope.openSetting();
      $scope.closePermissions();
    }
  };

  var getPermissions = function(callback) {
    $http.get(baseUrlSrv.getRestApiBase() + '/notebook/' + $scope.note.id + '/permissions').
    success(function(data, status, headers, config) {
      $scope.permissions = data.body;
      $scope.permissionsOrig = angular.copy($scope.permissions); // to check dirty
      if (callback) {
        callback();
      }
    }).
    error(function(data, status, headers, config) {
      if (status !== 0) {
        console.log('Error %o %o', status, data.message);
      }
    });
  };

  $scope.openPermissions = function() {
    $scope.showPermissions = true;
    getPermissions();
  };

  $scope.closePermissions = function() {
    if (isPermissionsDirty()) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Changes will be discarded.',
        callback: function(result) {
          if (result) {
            $scope.$apply(function() {
              $scope.showPermissions = false;
            });
          }
        }
      });
    } else {
      $scope.showPermissions = false;
    }
  };

  function convertPermissionsToArray() {
    if (!angular.isArray($scope.permissions.owners)) {
      $scope.permissions.owners = $scope.permissions.owners.split(',');
    }
    if (!angular.isArray($scope.permissions.readers)) {
      $scope.permissions.readers = $scope.permissions.readers.split(',');
    }
    if (!angular.isArray($scope.permissions.writers)) {
      $scope.permissions.writers = $scope.permissions.writers.split(',');
    }
  }

  $scope.savePermissions = function() {
    convertPermissionsToArray();
    $http.put(baseUrlSrv.getRestApiBase() + '/notebook/' + $scope.note.id + '/permissions',
      $scope.permissions, {withCredentials: true}).
      success(function(data, status, headers, config) {
        getPermissions(function() {
          console.log('Note permissions %o saved', $scope.permissions);
          BootstrapDialog.alert({
            closable: true,
            title: 'Permissions Saved Successfully!!!',
            message: 'Owners : ' + $scope.permissions.owners + '\n\n' + 'Readers : ' +
            $scope.permissions.readers + '\n\n' + 'Writers  : ' + $scope.permissions.writers
          });
          $scope.showPermissions = false;
        });
      }).
      error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
        BootstrapDialog.show({
          closable: false,
          closeByBackdrop: false,
          closeByKeyboard: false,
          title: 'Insufficient privileges',
          message: data.message,
          buttons: [
            {
              label: 'Login',
              action: function(dialog) {
                dialog.close();
                angular.element('#loginModal').modal({
                  show: 'true'
                });
              }
            },
            {
              label: 'Cancel',
              action: function(dialog) {
                dialog.close();
                $location.path('/');
              }
            }
          ]
        });
      });
  };

  $scope.togglePermissions = function() {
    if ($scope.showPermissions) {
      $scope.closePermissions();
    } else {
      $scope.openPermissions();
      $scope.closeSetting();
    }
  };

  var isSettingDirty = function() {
    if (angular.equals($scope.interpreterBindings, $scope.interpreterBindingsOrig)) {
      return false;
    } else {
      return true;
    }
  };

  var isPermissionsDirty = function() {
    if (angular.equals($scope.permissions, $scope.permissionsOrig)) {
      return false;
    } else {
      return true;
    }
  };

  function checkPreviousRole(role) {
    var i = 0;
    if (role !== $scope.role) {
      if ($scope.role === 'owners') {
        previousSelectedListOwners = [];
        for (i = 0; i < previousSelectedList.length; i++) {
          previousSelectedListOwners[i] = previousSelectedList[i];
        }
      }
      if ($scope.role === 'readers') {
        previousSelectedListReaders = [];
        for (i = 0; i < previousSelectedList.length; i++) {
          previousSelectedListReaders[i] = previousSelectedList[i];
        }
      }
      if ($scope.role === 'writers') {
        previousSelectedListWriters = [];
        for (i = 0; i < previousSelectedList.length; i++) {
          previousSelectedListWriters[i] = previousSelectedList[i];
        }
      }

      $scope.role = role;
      previousSelectedList = [];
      if (role === 'owners') {
        for (i = 0; i < previousSelectedListOwners.length; i++) {
          previousSelectedList[i] = previousSelectedListOwners[i];
        }
      }
      if (role === 'readers') {
        for (i = 0; i < previousSelectedListReaders.length; i++) {
          previousSelectedList[i] = previousSelectedListReaders[i];
        }
      }
      if (role === 'writers') {
        for (i = 0; i < previousSelectedListWriters.length; i++) {
          previousSelectedList[i] = previousSelectedListWriters[i];
        }
      }
    }
  }

  function convertToArray(role) {
    if (!$scope.permissions) {
      return;
    } else if (role === 'owners' && typeof $scope.permissions.owners === 'string') {
      searchText = $scope.permissions.owners.split(',');
    } else if (role === 'readers' && typeof $scope.permissions.readers === 'string') {
      searchText = $scope.permissions.readers.split(',');
    } else if (role === 'writers' && typeof $scope.permissions.writers === 'string') {
      searchText = $scope.permissions.writers.split(',');
    }

    for (var i = 0; i < searchText.length; i++) {
      searchText[i] = searchText[i].trim();
    }
  }

  function convertToString(role) {
    if (role === 'owners') {
      $scope.permissions.owners = searchText.join();
    } else if (role === 'readers') {
      $scope.permissions.readers = searchText.join();
    } else if (role === 'writers') {
      $scope.permissions.writers = searchText.join();
    }
  }

  function getSuggestions(searchQuery) {
    $scope.suggestions = [];
    $http.get(baseUrlSrv.getRestApiBase() + '/security/userlist/' + searchQuery).then(function
    (response) {
      var userlist = angular.fromJson(response.data).body;
      for (var k in userlist) {
        $scope.suggestions.push(userlist[k]);
      }
    });
  }

  function updatePreviousList() {
    for (var i = 0; i < searchText.length; i++) {
      previousSelectedList[i] = searchText[i];
    }
  }

  var getChangedIndex = function() {
    if (previousSelectedList.length === 0) {
      selectedUserIndex = searchText.length - 1;
    } else {
      for (var i = 0; i < searchText.length; i++) {
        if (previousSelectedList[i] !== searchText[i]) {
          selectedUserIndex = i;
          previousSelectedList = [];
          break;
        }
      }
    }
    updatePreviousList();
  };

  $scope.$watch('permissions.owners', _.debounce(function(readers) {
    $scope.$apply(function() {
      $scope.search('owners');
    });
  }, 350));

  $scope.$watch('permissions.readers', _.debounce(function(readers) {
    $scope.$apply(function() {
      $scope.search('readers');
    });
  }, 350));

  $scope.$watch('permissions.writers', _.debounce(function(readers) {
    $scope.$apply(function() {
      $scope.search('writers');
    });
  }, 350));

  // function to find suggestion list on change
  $scope.search = function(role) {
    angular.element('.userlist').show();
    convertToArray(role);
    checkPreviousRole(role);
    getChangedIndex();
    $scope.selectIndex = -1;
    $scope.suggestions = [];
    selectedUser = searchText[selectedUserIndex];
    if (selectedUser !== '') {
      getSuggestions(selectedUser);
    } else {
      $scope.suggestions = [];
    }
  };

  var checkIfSelected = function() {
    if (($scope.suggestions.length === 0) &&
      ($scope.selectIndex < 0 || $scope.selectIndex >= $scope.suggestions.length) ||
      ($scope.suggestions.length !== 0 && ($scope.selectIndex < 0 || $scope.selectIndex >= $scope.suggestions.length))
    ) {
      searchText[selectedUserIndex] = selectedUser;
      $scope.suggestions = [];
      return true;
    } else {
      return false;
    }
  };

  $scope.checkKeyDown = function(event, role) {
    if (event.keyCode === 40) {
      event.preventDefault();
      if ($scope.selectIndex + 1 !== $scope.suggestions.length) {
        $scope.selectIndex++;
      }
    } else if (event.keyCode === 38) {
      event.preventDefault();

      if ($scope.selectIndex - 1 !== -1) {
        $scope.selectIndex--;

      }
    } else if (event.keyCode === 13) {
      event.preventDefault();
      if (!checkIfSelected()) {
        selectedUser = $scope.suggestions[$scope.selectIndex];
        searchText[selectedUserIndex] = $scope.suggestions[$scope.selectIndex];
        updatePreviousList();
        convertToString(role);
        $scope.suggestions = [];
      }
    }
  };

  $scope.checkKeyUp = function(event) {
    if (event.keyCode !== 8 || event.keyCode !== 46) {
      if (searchText[selectedUserIndex] === '') {
        $scope.suggestions = [];
      }
    }
  };

  $scope.assignValueAndHide = function(index, role) {
    searchText[selectedUserIndex] = $scope.suggestions[index];
    updatePreviousList();
    convertToString(role);
    $scope.suggestions = [];
  };

  angular.element(document).click(function() {
    angular.element('.userlist').hide();
    angular.element('.ace_autocomplete').hide();
  });

  /*
  ** $scope.$on functions below
  */

  $scope.$on('setConnectedStatus', function(event, param) {
    if (connectedOnce && param) {
      initNotebook();
    }
    connectedOnce = true;
  });

  $scope.$on('moveParagraphUp', function(event, paragraphId) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i - 1;
        break;
      }
    }
    if (newIndex < 0 || newIndex >= $scope.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    var prevParagraphId = $scope.note.paragraphs[newIndex].id;
    angular.element('#' + paragraphId + '_paragraphColumn_main').scope().saveParagraph();
    angular.element('#' + prevParagraphId + '_paragraphColumn_main').scope().saveParagraph();
    websocketMsgSrv.moveParagraph(paragraphId, newIndex);
  });

  $scope.$on('moveParagraphDown', function(event, paragraphId) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i + 1;
        break;
      }
    }

    if (newIndex < 0 || newIndex >= $scope.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    var nextParagraphId = $scope.note.paragraphs[newIndex].id;
    angular.element('#' + paragraphId + '_paragraphColumn_main').scope().saveParagraph();
    angular.element('#' + nextParagraphId + '_paragraphColumn_main').scope().saveParagraph();
    websocketMsgSrv.moveParagraph(paragraphId, newIndex);
  });

  $scope.$on('moveFocusToPreviousParagraph', function(event, currentParagraphId) {
    var focus = false;
    for (var i = $scope.note.paragraphs.length - 1; i >= 0; i--) {
      if (focus === false) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
          focus = true;
          continue;
        }
      } else {
        $scope.$broadcast('focusParagraph', $scope.note.paragraphs[i].id, -1);
        break;
      }
    }
  });

  $scope.$on('moveFocusToNextParagraph', function(event, currentParagraphId) {
    var focus = false;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if (focus === false) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
          focus = true;
          continue;
        }
      } else {
        $scope.$broadcast('focusParagraph', $scope.note.paragraphs[i].id, 0);
        break;
      }
    }
  });

  $scope.$on('insertParagraph', function(event, paragraphId, position) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        //determine position of where to add new paragraph; default is below
        if (position === 'above') {
          newIndex = i;
        } else {
          newIndex = i + 1;
        }
        break;
      }
    }

    if (newIndex < 0 || newIndex > $scope.note.paragraphs.length) {
      return;
    }
    websocketMsgSrv.insertParagraph(newIndex);
  });

  $scope.$on('setNoteContent', function(event, note) {
    if (note === undefined) {
      $location.path('/');
    }

    $scope.paragraphUrl = $routeParams.paragraphId;
    $scope.asIframe = $routeParams.asIframe;
    if ($scope.paragraphUrl) {
      note = cleanParagraphExcept($scope.paragraphUrl, note);
      $rootScope.$broadcast('setIframe', $scope.asIframe);
    }

    if ($scope.note === null) {
      $scope.note = note;
    } else {
      updateNote(note);
    }
    initializeLookAndFeel();
    //open interpreter binding setting when there're none selected
    getInterpreterBindings(getInterpreterBindingsCallBack);
  });

  $scope.$on('$destroy', function() {
    angular.element(window).off('beforeunload');
    $scope.killSaveTimer();
    $scope.saveNote();

    document.removeEventListener('click', $scope.focusParagraphOnClick);
    document.removeEventListener('keydown', $scope.keyboardShortcut);
  });

});
