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

import './folder-permissions.css'

angular.module('zeppelinWebApp').controller('FolderPermissionsCtrl', folderPermissionsController)

function folderPermissionsController($scope, $rootScope, $http, baseUrlSrv) {
  'ngInject'

  $scope.folder = null

  $scope.$on('folderPermissions', function(event, folderId) {
    if (folderId.charAt(0) === '/') {
      folderId = folderId.substr(1, folderId.length - 1)
    }
    if ($scope.folder && $scope.folder.id === folderId) {
      getPermissions(folderId)
    }
  })

  $scope.$on('showFolderPermissionsModal', function(event, folder) {
    angular.element('#folderPermissionsModal').modal('show')
    $scope.folder = folder
    getPermissions(folder.id)
  })

  let getPermissions = function (folderId) {
    $http.get(baseUrlSrv.getRestApiBase() + '/notebook/folder/' + encodeURIComponent(folderId) +
      '/permissions')
      .success(function (data, status, headers, config) {
        $scope.permissions = data.body
        $scope.permissionsOrig = angular.copy($scope.permissions) // to check dirty

        let selectJson = {
          tokenSeparators: [',', ' '],
          ajax: {
            url: function (params) {
              if (!params.term) {
                return false
              }
              return baseUrlSrv.getRestApiBase() + '/security/userlist/' + params.term
            },
            delay: 250,
            processResults: function (data, params) {
              let results = []

              if (data.body.users.length !== 0) {
                let users = []
                for (let len = 0; len < data.body.users.length; len++) {
                  users.push({
                    'id': data.body.users[len],
                    'text': data.body.users[len]
                  })
                }
                results.push({
                  'text': 'Users :',
                  'children': users
                })
              }
              if (data.body.roles.length !== 0) {
                let roles = []
                for (let len = 0; len < data.body.roles.length; len++) {
                  roles.push({
                    'id': data.body.roles[len],
                    'text': data.body.roles[len]
                  })
                }
                results.push({
                  'text': 'Roles :',
                  'children': roles
                })
              }
              return {
                results: results,
                pagination: {
                  more: false
                }
              }
            },
            cache: false
          },
          width: ' ',
          tags: true,
          minimumInputLength: 3
        }

        angular.element('#selectOwners').select2(selectJson)
        angular.element('#selectReaders').select2(selectJson)
        angular.element('#selectWriters').select2(selectJson)
        angular.element('#selectRunners').select2(selectJson)
      })
      .error(function (data, status, headers, config) {
        if (status !== 0) {
          console.log('Error %o %o', status, data.message)
        }
      })
  }

  $scope.savePermissions = function () {
    if ($scope.isAnonymous || $rootScope.ticket.principal.trim().length === 0) {
      $scope.blockAnonUsers()
    }
    convertPermissionsToArray()
    if (isPermissionsListEmpty($scope.permissions.owners)) {
      if (isPermissionsListEmpty($scope.permissions.writers) &&
        isPermissionsListEmpty($scope.permissions.readers) &&
        isPermissionsListEmpty($scope.permissions.runners)) {
        $scope.setPermissions()
      } else {
        BootstrapDialog.show({
          closable: false,
          title: 'Setting Owners Permissions',
          message: 'Please fill the [Owners] field. If not, it will set as current user.\n\n' +
          'Current user : [ ' + $rootScope.ticket.principal + ']',
          buttons: [
            {
              label: 'Set',
              action: function (dialog) {
                dialog.close()
                $scope.permissions.owners = [$rootScope.ticket.principal]
                $scope.setPermissions()
              }
            },
            {
              label: 'Cancel',
              action: function (dialog) {
                dialog.close()
              }
            }
          ]
        })
      }
    } else {
      $scope.setPermissions()
    }
  }

  let isPermissionsListEmpty = function(permissionsList) {
    if (permissionsList.length > 0) {
      for (let i = 0; i < permissionsList.length; i++) {
        if (permissionsList[i].trim().length > 0) {
          return false
        } else if (i === permissionsList.length - 1) {
          return true
        }
      }
    } else {
      return true
    }
  }

  $scope.blockAnonUsers = function () {
    let zeppelinVersion = $rootScope.zeppelinVersion
    let url = 'https://zeppelin.apache.org/docs/' + zeppelinVersion + '/security/notebook_authorization.html'
    let content = 'Only authenticated user can set the permission.' +
      '<a data-toggle="tooltip" data-placement="top" title="Learn more" target="_blank" href=' + url + '>' +
      '<i class="icon-question" />' +
      '</a>'
    BootstrapDialog.show({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: 'No permission',
      message: content,
      buttons: [{
        label: 'Close',
        action: function (dialog) {
          dialog.close()
        }
      }]
    })
  }

  function convertPermissionsToArray () {
    $scope.permissions.owners = angular.element('#selectOwners').val()
    $scope.permissions.readers = angular.element('#selectReaders').val()
    $scope.permissions.writers = angular.element('#selectWriters').val()
    $scope.permissions.runners = angular.element('#selectRunners').val()
    angular.element('.permissionsForm select').find('option:not([is-select2="false"])').remove()
  }

  $scope.setPermissions = function() {
    $http.put(baseUrlSrv.getRestApiBase() + '/notebook/folder/' + encodeURIComponent($scope.folder.id) + '/permissions',
      $scope.permissions, {withCredentials: true})
      .success(function (data, status, headers, config) {
        console.log('Folder permissions %o saved', $scope.permissions)
        BootstrapDialog.alert({
          closable: true,
          title: 'Permissions Saved Successfully',
          message: 'Owners : ' + $scope.permissions.owners + '\n\n' + 'Readers : ' +
          $scope.permissions.readers + '\n\n' + 'Writers  : ' + $scope.permissions.writers +
          '\n\n' + ' Runners  : ' + $scope.permissions.runners
        })
        angular.element('#folderPermissionsModal').modal('hide')
      })
      .error(function (data, status, headers, config) {
        console.log('Error %o %o', status, data.message)
        BootstrapDialog.show({
          closable: false,
          closeByBackdrop: false,
          closeByKeyboard: false,
          title: 'Insufficient privileges',
          message: data.message,
          buttons: [
            {
              label: 'Login',
              action: function (dialog) {
                dialog.close()
                angular.element('#loginModal').modal({
                  show: 'true'
                })
              }
            },
            {
              label: 'Cancel',
              action: function (dialog) {
                dialog.close()
              }
            }
          ]
        })
      })
  }

  $scope.closePermissions = function () {
    if (isPermissionsDirty()) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Changes will be discarded.',
        callback: function (result) {
          if (result) {
            $scope.$apply(function () {
              angular.element('#folderPermissionsModal').modal('hide')
            })
          }
        }
      })
    } else {
      angular.element('#folderPermissionsModal').modal('hide')
    }
  }

  const isPermissionsDirty = function () {
    return !angular.equals($scope.permissions, $scope.permissionsOrig)
  }
}
