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

angular.module('zeppelinWebApp').controller('NavCtrl', NavCtrl)

function NavCtrl ($scope, $rootScope, $http, $routeParams, $location,
                 noteListFactory, baseUrlSrv, websocketMsgSrv,
                 arrayOrderingSrv, searchService, TRASH_FOLDER_ID) {
  'ngInject'

  let vm = this
  vm.arrayOrderingSrv = arrayOrderingSrv
  vm.connected = websocketMsgSrv.isConnected()
  vm.isActive = isActive
  vm.logout = logout
  vm.notes = noteListFactory
  vm.search = search
  vm.searchForm = searchService
  vm.showLoginWindow = showLoginWindow
  vm.TRASH_FOLDER_ID = TRASH_FOLDER_ID
  vm.isFilterNote = isFilterNote

  $scope.query = {q: ''}

  initController()

  function getZeppelinVersion () {
    $http.get(baseUrlSrv.getRestApiBase() + '/version').success(
      function (data, status, headers, config) {
        $rootScope.zeppelinVersion = data.body.version
      }).error(
      function (data, status, headers, config) {
        console.log('Error %o %o', status, data.message)
      })
  }

  function initController () {
    $scope.isDrawNavbarNoteList = false
    angular.element('#notebook-list').perfectScrollbar({suppressScrollX: true})

    angular.element(document).click(function () {
      $scope.query.q = ''
    })

    getZeppelinVersion()
    loadNotes()
  }

  function isFilterNote (note) {
    if (!$scope.query.q) {
      return true
    }

    let noteName = note.name
    if (noteName.toLowerCase().indexOf($scope.query.q.toLowerCase()) > -1) {
      return true
    }
    return false
  }

  function isActive (noteId) {
    return ($routeParams.noteId === noteId)
  }

  function listConfigurations () {
    websocketMsgSrv.listConfigurations()
  }

  function loadNotes () {
    websocketMsgSrv.getNoteList()
  }

  function getHomeNote () {
    websocketMsgSrv.getHomeNote()
  }

  function logout () {
    let logoutURL = baseUrlSrv.getRestApiBase() + '/login/logout'

    // for firefox and safari
    logoutURL = logoutURL.replace('//', '//false:false@')
    $http.post(logoutURL).error(function () {
      // force authcBasic (if configured) to logout
      $http.post(logoutURL).error(function () {
        $rootScope.userName = ''
        $rootScope.ticket.principal = ''
        $rootScope.ticket.screenUsername = ''
        $rootScope.ticket.ticket = ''
        $rootScope.ticket.roles = ''
        BootstrapDialog.show({
          message: 'Logout Success'
        })
        setTimeout(function () {
          window.location = baseUrlSrv.getBase()
        }, 1000)
      })
    })
  }

  function search (searchTerm) {
    $location.path('/search/' + searchTerm)
  }

  function showLoginWindow () {
    setTimeout(function () {
      angular.element('#userName').focus()
    }, 500)
  }

  /*
   ** $scope.$on functions below
   */

  $scope.$on('setNoteMenu', function (event, notes) {
    noteListFactory.setNotes(notes)
    initNotebookListEventListener()
  })

  $scope.$on('setConnectedStatus', function (event, param) {
    vm.connected = param
  })

  $scope.$on('loginSuccess', function (event, param) {
    $rootScope.ticket.screenUsername = $rootScope.ticket.principal
    listConfigurations()
    loadNotes()
    getHomeNote()
  })

  /*
   ** Performance optimization for Browser Render.
   */
  function initNotebookListEventListener () {
    angular.element(document).ready(function () {
      angular.element('.notebook-list-dropdown').on('show.bs.dropdown', function () {
        $scope.isDrawNavbarNoteList = true
      })

      angular.element('.notebook-list-dropdown').on('hide.bs.dropdown', function () {
        $scope.isDrawNavbarNoteList = false
      })
    })
  }

  $scope.calculateTooltipPlacement = function (note) {
    if (note !== undefined && note.name !== undefined) {
      let length = note.name.length
      if (length < 2) {
        return 'top-left'
      } else if (length > 7) {
        return 'top-right'
      }
    }
    return 'top'
  }
}
