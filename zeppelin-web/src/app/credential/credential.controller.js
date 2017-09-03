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

angular.module('zeppelinWebApp').controller('CredentialCtrl', CredentialController)

function CredentialController($scope, $http, baseUrlSrv, ngToast) {
  'ngInject'

  ngToast.dismiss()

  $scope.credentialInfo = []
  $scope.showAddNewCredentialInfo = false
  $scope.availableInterpreters = []

  $scope.entity = ''
  $scope.password = ''
  $scope.username = ''

  $scope.hasCredential = () => {
    return Array.isArray($scope.credentialInfo) && $scope.credentialInfo.length
  }

  let getCredentialInfo = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/credential')
      .success(function (data, status, headers, config) {
        $scope.credentialInfo.length = 0 // keep the ref while cleaning
        const returnedCredentials = data.body.userCredentials

        for (let key in returnedCredentials) {
          const value = returnedCredentials[key]
          $scope.credentialInfo.push({
            entity: key,
            password: value.password,
            username: value.username,
          })
        }

        console.log('Success %o %o', status, $scope.credentialInfo)
      })
      .error(function (data, status, headers, config) {
        if (status === 401) {
          showToast('You do not have permission on this page', 'danger')
          setTimeout(function () {
            window.location = baseUrlSrv.getBase()
          }, 3000)
        }
        console.log('Error %o %o', status, data.message)
      })
  }

  $scope.isValidCredential = function() {
    return $scope.entity.trim() !== '' && $scope.username.trim() !== ''
  }

  $scope.addNewCredentialInfo = function () {
    if (!$scope.isValidCredential()) {
      showToast('Username \\ Entity can not be empty.', 'danger')
      return
    }

    let newCredential = {
      'entity': $scope.entity,
      'username': $scope.username,
      'password': $scope.password
    }

    $http.put(baseUrlSrv.getRestApiBase() + '/credential', newCredential)
      .success(function (data, status, headers, config) {
        showToast('Successfully saved credentials.', 'success')
        $scope.credentialInfo.push(newCredential)
        resetCredentialInfo()
        $scope.showAddNewCredentialInfo = false
        console.log('Success %o %o', status, data.message)
      })
      .error(function (data, status, headers, config) {
        showToast('Error saving credentials', 'danger')
        console.log('Error %o %o', status, data.message)
      })
  }

  let getAvailableInterpreters = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
      .success(function (data, status, headers, config) {
        for (let setting = 0; setting < data.body.length; setting++) {
          $scope.availableInterpreters.push(
            data.body[setting].group + '.' + data.body[setting].name)
        }
        angular.element('#entityname').autocomplete({
          source: $scope.availableInterpreters,
          select: function (event, selected) {
            $scope.entity = selected.item.value
            return false
          }
        })
      })
      .error(function (data, status, headers, config) {
        showToast(data.message, 'danger')
        console.log('Error %o %o', status, data.message)
      })
  }

  $scope.toggleAddNewCredentialInfo = function () {
    if ($scope.showAddNewCredentialInfo) {
      $scope.showAddNewCredentialInfo = false
    } else {
      $scope.showAddNewCredentialInfo = true
    }
  }

  $scope.cancelCredentialInfo = function () {
    $scope.showAddNewCredentialInfo = false
    resetCredentialInfo()
  }

  const resetCredentialInfo = function () {
    $scope.entity = ''
    $scope.username = ''
    $scope.password = ''
  }

  $scope.copyOriginCredentialsInfo = function () {
    showToast('Since entity is a unique key, you can edit only username & password', 'info')
  }

  $scope.updateCredentialInfo = function (form, data, entity) {
    if (!$scope.isValidCredential()) {
      showToast('Username \\ Entity can not be empty.', 'danger')
      return
    }

    let credential = {
      entity: entity,
      username: data.username,
      password: data.password
    }

    $http.put(baseUrlSrv.getRestApiBase() + '/credential/', credential)
      .success(function (data, status, headers, config) {
        const index = $scope.credentialInfo.findIndex(elem => elem.entity === entity)
        $scope.credentialInfo[index] = credential
        return true
      })
      .error(function (data, status, headers, config) {
        showToast('We could not save the credential', 'danger')
        console.log('Error %o %o', status, data.message)
        form.$show()
      })
    return false
  }

  $scope.removeCredentialInfo = function (entity) {
    BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to delete this credential information?',
      callback: function (result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/credential/' + entity)
            .success(function (data, status, headers, config) {
              const index = $scope.credentialInfo.findIndex(elem => elem.entity === entity)
              $scope.credentialInfo.splice(index, 1)
              console.log('Success %o %o', status, data.message)
            })
            .error(function (data, status, headers, config) {
              showToast(data.message, 'danger')
              console.log('Error %o %o', status, data.message)
            })
        }
      }
    })
  }

  function showToast(message, type) {
    const verticalPosition = 'bottom'
    const timeout = '3000'

    if (type === 'success') {
      ngToast.success({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    } else if (type === 'info') {
      ngToast.info({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    } else {
      ngToast.danger({ content: message, verticalPosition: verticalPosition, timeout: timeout, })
    }
  }

  let init = function () {
    getAvailableInterpreters()
    getCredentialInfo()
  }

  init()
}
