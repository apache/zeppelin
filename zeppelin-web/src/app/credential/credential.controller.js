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

angular.module('zeppelinWebApp').controller('CredentialCtrl', CredentialController);

function CredentialController($scope, $rootScope, $http, baseUrlSrv, ngToast) {
  'ngInject';

  ngToast.dismiss();

  $scope.credentialInfo = [];
  $scope.showAddNewCredentialInfo = false;
  $scope.availableInterpreters = [];

  $scope.entity = '';
  $scope.password = '';
  $scope.username = '';
  $scope.readers = [];
  $scope.owners = [];

  $scope.hasCredential = () => {
    return Array.isArray($scope.credentialInfo) && $scope.credentialInfo.length;
  };

  let getCredentialInfo = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/credential')
      .success(function(data, status, headers, config) {
        $scope.credentialInfo.length = 0; // keep the ref while cleaning
        const returnedCredentials = data.body;

        for (let key in returnedCredentials) {
          if (returnedCredentials.hasOwnProperty(key)) {
            const value = returnedCredentials[key];
            $scope.credentialInfo.push({
              entity: key,
              password: value.password,
              username: value.username,
              readers: value.readers,
              owners: value.owners,
            });
          }
        }

        console.log('Success %o %o', status, $scope.credentialInfo);
      })
      .error(function(data, status, headers, config) {
        if (status === 401) {
          showToast('You do not have permission on this page', 'danger');
          setTimeout(function() {
            window.location = baseUrlSrv.getBase();
          }, 3000);
        }
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.isValidCredential = function() {
    return $scope.entity.trim() !== '' && $scope.username.trim() !== '';
  };

  $scope.addNewCredentialInfo = function() {
    if (!$scope.isValidCredential()) {
      showToast('Username \\ Entity can not be empty.', 'danger');
      return;
    }

    let newCredential = {
      'entity': $scope.entity,
      'username': $scope.username,
      'password': $scope.password,
      'owners': angular.element('#NewCredentialOwners').val(),
      'readers': angular.element('#NewCredentialReaders').val(),
    };

    for (let i = 0; i < newCredential.owners.length; i++) {
      newCredential.owners[i] = newCredential.owners[i].trim();
    }
    for (let i = 0; i < newCredential.readers.length; i++) {
      newCredential.readers[i] = newCredential.readers[i].trim();
    }
    $http.put(baseUrlSrv.getRestApiBase() + '/credential', newCredential)
      .success(function(data, status, headers, config) {
        showToast('Successfully saved credentials.', 'success');
        $scope.credentialInfo.push(newCredential);
        resetCredentialInfo();
        $scope.showAddNewCredentialInfo = false;
        console.log('Success %o %o', status, data.message);
      })
      .error(function(data, status, headers, config) {
        showToast('Error saving credentials', 'danger');
        console.log('Error %o %o', status, data.message);
      });
  };

  let getAvailableInterpreters = function() {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
      .success(function(data, status, headers, config) {
        for (let setting = 0; setting < data.body.length; setting++) {
          $scope.availableInterpreters.push(
            data.body[setting].name);
        }
        angular.element('#entityname').autocomplete({
          source: $scope.availableInterpreters,
          select: function(event, selected) {
            $scope.entity = selected.item.value;
            return false;
          },
        });
      })
      .error(function(data, status, headers, config) {
        showToast(data.message, 'danger');
        console.log('Error %o %o', status, data.message);
      });
  };

  $scope.toggleAddNewCredentialInfo = function() {
    if ($scope.showAddNewCredentialInfo) {
      $scope.showAddNewCredentialInfo = false;
      angular.element('#NewCredentialOwners').select2({});
      angular.element('#NewCredentialReaders').select2({});
    } else {
      let initialOwner = $rootScope.ticket.principal === 'anonymous' ? [] : [$rootScope.ticket.principal];
      $scope.showAddNewCredentialInfo = true;
      $scope.owners = initialOwner;
      $scope.readers = [];
      angular.element('#NewCredentialOwners').select2(getSelectJson());
      angular.element('#NewCredentialReaders').select2(getSelectJson());
      if (initialOwner.length) {
        let initialOwnerOption = new Option(initialOwner[0], initialOwner[0], true, false);
        angular.element('#NewCredentialOwners').append(initialOwnerOption).trigger('change');
      }
      angular.element('#NewCredentialOwners').val(null).trigger('change');
      angular.element('#NewCredentialReaders').val(null).trigger('change');
    }
  };

  $scope.cancelCredentialInfo = function() {
    $scope.showAddNewCredentialInfo = false;
    resetCredentialInfo();
  };

  const resetCredentialInfo = function() {
    $scope.entity = '';
    $scope.username = '';
    $scope.password = '';
    $scope.readers = [];
    $scope.owners = [];
  };

  $scope.copyOriginCredentialsInfo = function() {
    showToast('Since entity is a unique key, you can edit only username & password', 'info');
  };

  let renderOwnersReadersSelect2 = function(entity) {
    angular.element('#' + entity + 'Owners').select2(getSelectJson());
    angular.element('#' + entity + 'Readers').select2(getSelectJson());
  };

  $scope.updateCredentialInfo = function(form, data, entity) {
    if (!data.username || !data.password) {
      showToast('Username \\ Password can not be empty.', 'danger');
      return false;
    }

    let credential = {
      entity: entity,
      username: data.username,
      password: data.password,
      owners: angular.element('#' + entity + 'Owners').val(),
      readers: angular.element('#' + entity + 'Readers').val(),
    };

    for (let i = 0; i < credential.owners.length; i++) {
      credential.owners[i] = credential.owners[i].trim();
    }
    for (let i = 0; i < credential.readers.length; i++) {
      credential.readers[i] = credential.readers[i].trim();
    }
    $http.put(baseUrlSrv.getRestApiBase() + '/credential/', credential)
      .success(function(data, status, headers, config) {
        const index = $scope.credentialInfo.findIndex((elem) => elem.entity === entity);
        $scope.credentialInfo[index] = credential;
        setTimeout(function() {
          renderOwnersReadersSelect2(entity);
        }, 100);
        return true;
      })
      .error(function(data, status, headers, config) {
        showToast('We could not save the credential', 'danger');
        console.log('Error %o %o', status, data.message);
        form.$show();
        setTimeout(function() {
          renderOwnersReadersSelect2(entity);
        }, 100);
      });
    return false;
  };

  $scope.removeCredentialInfo = function(entity) {
    BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to delete this credential information?',
      callback: function(result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/credential/' + entity)
            .success(function(data, status, headers, config) {
              const index = $scope.credentialInfo.findIndex((elem) => elem.entity === entity);
              $scope.credentialInfo.splice(index, 1);
              console.log('Success %o %o', status, data.message);
            })
            .error(function(data, status, headers, config) {
              showToast(data.message, 'danger');
              console.log('Error %o %o', status, data.message);
            });
        }
      },
    });
  };

  function showToast(message, type) {
    const verticalPosition = 'bottom';
    const timeout = '3000';

    if (type === 'success') {
      ngToast.success({content: message, verticalPosition: verticalPosition, timeout: timeout});
    } else if (type === 'info') {
      ngToast.info({content: message, verticalPosition: verticalPosition, timeout: timeout});
    } else {
      ngToast.danger({content: message, verticalPosition: verticalPosition, timeout: timeout});
    }
  }

  let getSelectJson = function() {
    let selectJson = {
      tags: true,
      minimumInputLength: 3,
      multiple: true,
      tokenSeparators: [',', ' '],
      ajax: {
        url: function(params) {
          if (!params.term) {
            return false;
          }
          return baseUrlSrv.getRestApiBase() + '/security/userlist/' + params.term;
        },
        delay: 250,
        processResults: function(data, params) {
          let results = [];

          if (data.body.users.length !== 0) {
            let users = [];
            for (let len = 0; len < data.body.users.length; len++) {
              users.push({
                'id': data.body.users[len],
                'text': data.body.users[len],
              });
            }
            results.push({
              'text': 'Users :',
              'children': users,
            });
          }
          if (data.body.roles.length !== 0) {
            let roles = [];
            for (let len = 0; len < data.body.roles.length; len++) {
              roles.push({
                'id': data.body.roles[len],
                'text': data.body.roles[len],
              });
            }
            results.push({
              'text': 'Roles :',
              'children': roles,
            });
          }
          return {
            results: results,
            pagination: {
              more: false,
            },
          };
        },
        cache: false,
      },
    };
    return selectJson;
  };

  $scope.$on('ngRenderFinished', function(event, data) {
    for (let credential = 0; credential < $scope.credentialInfo.length; credential++) {
      renderOwnersReadersSelect2($scope.credentialInfo[credential].entity);
    }
  });

  $scope.getCredentialDocsLink = function() {
    const currentVersion = $rootScope.zeppelinVersion;
    const isVersionOver0Point7 = currentVersion && currentVersion.split('.')[1] > 7;
    /*
     * Add '/setup' to doc link on the version over 0.7.0
     */
    return `https://zeppelin.apache.org/docs/${currentVersion}${
      isVersionOver0Point7 ? '/setup' : ''
    }/security/datasource_authorization.html`;
  };

  let init = function() {
    getAvailableInterpreters();
    getCredentialInfo();
  };

  init();
}
