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

function CredentialController($scope, $http, baseUrlSrv, ErrorHandlerService) {
  'ngInject'

  let ehs = ErrorHandlerService
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
      .then(res => {
        $scope.credentialInfo.length = 0 // keep the ref while cleaning
        const returnedCredentials = res.data.body.userCredentials

        for (let key in returnedCredentials) {
          const value = returnedCredentials[key]
          $scope.credentialInfo.push({
            entity: key,
            password: value.password,
            username: value.username,
          })
        }
      })
      .catch(ehs.handleHttpError('Failed to get credentials'))
  }

  $scope.isValidCredential = function() {
    return $scope.entity.trim() !== '' && $scope.username.trim() !== ''
  }

  $scope.addNewCredentialInfo = function () {
    if (!$scope.isValidCredential()) {
      ehs.showToast('Username \\ Entity can not be empty.', 'danger')
      return
    }

    let newCredential = {
      'entity': $scope.entity,
      'username': $scope.username,
      'password': $scope.password
    }

    $http.put(baseUrlSrv.getRestApiBase() + '/credential', newCredential)
      .then(res => {
        ehs.showToast('Successfully saved credentials.', 'success')
        $scope.credentialInfo.push(newCredential)
        resetCredentialInfo()
        $scope.showAddNewCredentialInfo = false
      })
      .catch(ehs.handleHttpError('Failed to add new credential'))
  }

  let getAvailableInterpreters = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
      .then(res => {
        const interpreters = res.data.body
        for (let setting = 0; setting < interpreters.length; setting++) {
          $scope.availableInterpreters.push(
            interpreters[setting].group + '.' + interpreters[setting].name)
        }
        angular.element('#entityname').autocomplete({
          source: $scope.availableInterpreters,
          select: function (event, selected) {
            $scope.entity = selected.item.value
            return false
          }
        })
      })
      .catch(ehs.handleHttpError('Failed to get available interpreters'))
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
    ehs.showToast('Since entity is a unique key, you can edit only username & password', 'info')
  }

  $scope.updateCredentialInfo = function (form, data, entity) {
    if (!$scope.isValidCredential()) {
      ehs.showToast('Username \\ Entity can not be empty.', 'danger')
      return
    }

    let credential = {
      entity: entity,
      username: data.username,
      password: data.password
    }

    $http.put(baseUrlSrv.getRestApiBase() + '/credential/', credential)
      .then(res => {
        const index = $scope.credentialInfo.findIndex(elem => elem.entity === entity)
        $scope.credentialInfo[index] = credential
        return true
      })
      .catch((res) => {
        ehs.handleHttpError('Failed to update credential')(res)
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
            .then(res => {
              const index = $scope.credentialInfo.findIndex(elem => elem.entity === entity)
              $scope.credentialInfo.splice(index, 1)
            })
            .catch(ehs.handleHttpError('Failed to remove credential'))
        }
      }
    })
  }

  let init = function () {
    getAvailableInterpreters()
    getCredentialInfo()
  }

  init()
}
