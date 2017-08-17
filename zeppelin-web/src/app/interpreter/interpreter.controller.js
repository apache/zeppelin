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

import { ParagraphStatus, } from '../notebook/paragraph/paragraph.status'

angular.module('zeppelinWebApp').controller('InterpreterCtrl', InterpreterCtrl)

function InterpreterCtrl($rootScope, $scope, $http, baseUrlSrv, ngToast, $timeout, $route) {
  'ngInject'

  let interpreterSettingsTmp = []
  $scope.interpreterSettings = []
  $scope.availableInterpreters = {}
  $scope.showAddNewSetting = false
  $scope.showRepositoryInfo = false
  $scope.searchInterpreter = ''
  $scope._ = _
  $scope.interpreterPropertyTypes = []
  ngToast.dismiss()

  $scope.openPermissions = function () {
    $scope.showInterpreterAuth = true
  }

  $scope.closePermissions = function () {
    $scope.showInterpreterAuth = false
  }

  let getSelectJson = function () {
    let selectJson = {
      tags: true,
      minimumInputLength: 3,
      multiple: true,
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
      }
    }
    return selectJson
  }

  $scope.togglePermissions = function (intpName) {
    angular.element('#' + intpName + 'Owners').select2(getSelectJson())
    if ($scope.showInterpreterAuth) {
      $scope.closePermissions()
    } else {
      $scope.openPermissions()
    }
  }

  $scope.$on('ngRenderFinished', function (event, data) {
    for (let setting = 0; setting < $scope.interpreterSettings.length; setting++) {
      angular.element('#' + $scope.interpreterSettings[setting].name + 'Owners').select2(getSelectJson())
    }
  })

  let getInterpreterSettings = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
      .then(function (res) {
        $scope.interpreterSettings = res.data.body
        checkDownloadingDependencies()
      }).catch(function (res) {
        if (res.status === 401) {
          ngToast.danger({
            content: 'You don\'t have permission on this page',
            verticalPosition: 'bottom',
            timeout: '3000'
          })
          setTimeout(function () {
            window.location = baseUrlSrv.getBase()
          }, 3000)
        }
        console.log('Error %o %o', res.status, res.data ? res.data.message : '')
      })
  }

  const checkDownloadingDependencies = function () {
    let isDownloading = false
    for (let index = 0; index < $scope.interpreterSettings.length; index++) {
      let setting = $scope.interpreterSettings[index]
      if (setting.status === 'DOWNLOADING_DEPENDENCIES') {
        isDownloading = true
      }

      if (setting.status === ParagraphStatus.ERROR || setting.errorReason) {
        ngToast.danger({content: 'Error setting properties for interpreter \'' +
        setting.group + '.' + setting.name + '\': ' + setting.errorReason,
          verticalPosition: 'top',
          dismissOnTimeout: false
        })
      }
    }

    if (isDownloading) {
      $timeout(function () {
        if ($route.current.$$route.originalPath === '/interpreter') {
          getInterpreterSettings()
        }
      }, 2000)
    }
  }

  let getAvailableInterpreters = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter').then(function (res) {
      $scope.availableInterpreters = res.data.body
    }).catch(function (res) {
      console.log('Error %o %o', res.status, res.data ? res.data.message : '')
    })
  }

  let getAvailableInterpreterPropertyWidgets = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/property/types')
      .then(function (res) {
        $scope.interpreterPropertyTypes = res.data.body
      }).catch(function (res) {
        console.log('Error %o %o', res.status, res.data ? res.data.message : '')
      })
  }

  let emptyNewProperty = function(object) {
    angular.extend(object, {propertyValue: '', propertyKey: '', propertyType: $scope.interpreterPropertyTypes[0]})
  }

  let emptyNewDependency = function (object) {
    angular.extend(object, {depArtifact: '', depExclude: ''})
  }

  let removeTMPSettings = function (index) {
    interpreterSettingsTmp.splice(index, 1)
  }

  $scope.copyOriginInterpreterSettingProperties = function (settingId) {
    let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
    interpreterSettingsTmp[index] = angular.copy($scope.interpreterSettings[index])
  }

  $scope.setPerNoteOption = function (settingId, sessionOption) {
    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }

    if (sessionOption === 'isolated') {
      option.perNote = sessionOption
      option.session = false
      option.process = true
    } else if (sessionOption === 'scoped') {
      option.perNote = sessionOption
      option.session = true
      option.process = false
    } else {
      option.perNote = 'shared'
      option.session = false
      option.process = false
    }
  }

  $scope.defaultValueByType = function (setting) {
    if (setting.propertyType === 'checkbox') {
      setting.propertyValue = false
      return
    }

    setting.propertyValue = ''
  }

  $scope.setPerUserOption = function (settingId, sessionOption) {
    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }

    if (sessionOption === 'isolated') {
      option.perUser = sessionOption
      option.session = false
      option.process = true
    } else if (sessionOption === 'scoped') {
      option.perUser = sessionOption
      option.session = true
      option.process = false
    } else {
      option.perUser = 'shared'
      option.session = false
      option.process = false
    }
  }

  $scope.getPerNoteOption = function (settingId) {
    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }

    if (option.perNote === 'scoped') {
      return 'scoped'
    } else if (option.perNote === 'isolated') {
      return 'isolated'
    } else {
      return 'shared'
    }
  }

  $scope.getPerUserOption = function (settingId) {
    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }

    if (option.perUser === 'scoped') {
      return 'scoped'
    } else if (option.perUser === 'isolated') {
      return 'isolated'
    } else {
      return 'shared'
    }
  }

  $scope.getInterpreterRunningOption = function (settingId) {
    let sharedModeName = 'shared'

    let globallyModeName = 'Globally'
    let perNoteModeName = 'Per Note'
    let perUserModeName = 'Per User'

    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }

    let perNote = option.perNote
    let perUser = option.perUser

    // Globally == shared_perNote + shared_perUser
    if (perNote === sharedModeName && perUser === sharedModeName) {
      return globallyModeName
    }

    if ($rootScope.ticket.ticket === 'anonymous' && $rootScope.ticket.roles === '[]') {
      if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
        return perNoteModeName
      }
    } else if ($rootScope.ticket.ticket !== 'anonymous') {
      if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
        if (perUser !== undefined && typeof perUser === 'string' && perUser !== '') {
          return perUserModeName
        }
        return perNoteModeName
      }
    }

    option.perNote = sharedModeName
    option.perUser = sharedModeName
    return globallyModeName
  }

  $scope.setInterpreterRunningOption = function (settingId, isPerNoteMode, isPerUserMode) {
    let option
    if (settingId === undefined) {
      option = $scope.newInterpreterSetting.option
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      option = setting.option
    }
    option.perNote = isPerNoteMode
    option.perUser = isPerUserMode
  }

  $scope.updateInterpreterSetting = function (form, settingId) {
    const thisConfirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to update this interpreter and restart with new settings?',
      callback: function (result) {
        if (result) {
          let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
          let setting = $scope.interpreterSettings[index]
          if (setting.propertyKey !== '' || setting.propertyKey) {
            $scope.addNewInterpreterProperty(settingId)
          }
          if (setting.depArtifact !== '' || setting.depArtifact) {
            $scope.addNewInterpreterDependency(settingId)
          }
          // add missing field of option
          if (!setting.option) {
            setting.option = {}
          }
          if (setting.option.isExistingProcess === undefined) {
            setting.option.isExistingProcess = false
          }
          if (setting.option.setPermission === undefined) {
            setting.option.setPermission = false
          }
          if (setting.option.isUserImpersonate === undefined) {
            setting.option.isUserImpersonate = false
          }
          if (!($scope.getInterpreterRunningOption(settingId) === 'Per User' &&
            $scope.getPerUserOption(settingId) === 'isolated')) {
            setting.option.isUserImpersonate = false
          }
          if (setting.option.remote === undefined) {
            // remote always true for now
            setting.option.remote = true
          }
          setting.option.owners = angular.element('#' + setting.name + 'Owners').val()

          let request = {
            option: angular.copy(setting.option),
            properties: angular.copy(setting.properties),
            dependencies: angular.copy(setting.dependencies)
          }

          thisConfirm.$modalFooter.find('button').addClass('disabled')
          thisConfirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Saving Setting')

          $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId, request)
            .then(function (res) {
              $scope.interpreterSettings[index] = res.data.body
              removeTMPSettings(index)
              checkDownloadingDependencies()
              thisConfirm.close()
            })
            .catch(function (res) {
              const message = res.data ? res.data.message : 'Could not connect to server.'
              console.log('Error %o %o', res.status, message)
              ngToast.danger({content: message, verticalPosition: 'bottom'})
              form.$show()
              thisConfirm.close()
            })
          return false
        } else {
          form.$show()
        }
      }
    })
  }

  $scope.resetInterpreterSetting = function (settingId) {
    let index = _.findIndex($scope.interpreterSettings, {'id': settingId})

    // Set the old settings back
    $scope.interpreterSettings[index] = angular.copy(interpreterSettingsTmp[index])
    removeTMPSettings(index)
  }

  $scope.removeInterpreterSetting = function (settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to delete this interpreter setting?',
      callback: function (result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId)
            .then(function (res) {
              let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
              $scope.interpreterSettings.splice(index, 1)
            }).catch(function (res) {
              console.log('Error %o %o', res.status, res.data ? res.data.message : '')
            })
        }
      }
    })
  }

  $scope.newInterpreterGroupChange = function () {
    let el = _.pluck(_.filter($scope.availableInterpreters, {'name': $scope.newInterpreterSetting.group}),
      'properties')
    let properties = {}
    for (let i = 0; i < el.length; i++) {
      let intpInfo = el[i]
      for (let key in intpInfo) {
        properties[key] = {
          value: intpInfo[key].defaultValue,
          description: intpInfo[key].description,
          type: intpInfo[key].type
        }
      }
    }
    $scope.newInterpreterSetting.properties = properties
  }

  $scope.restartInterpreterSetting = function (settingId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to restart this interpreter?',
      callback: function (result) {
        if (result) {
          $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/restart/' + settingId)
            .then(function (res) {
              let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
              $scope.interpreterSettings[index] = res.data.body
              ngToast.info('Interpreter stopped. Will be lazily started on next run.')
            }).catch(function (res) {
              let errorMsg = (res.data !== null) ? res.data.message : 'Could not connect to server.'
              console.log('Error %o %o', res.status, errorMsg)
              ngToast.danger(errorMsg)
            })
        }
      }
    })
  }

  $scope.addNewInterpreterSetting = function () {
    // user input validation on interpreter creation
    if (!$scope.newInterpreterSetting.name ||
      !$scope.newInterpreterSetting.name.trim() || !$scope.newInterpreterSetting.group) {
      BootstrapDialog.alert({
        closable: true,
        title: 'Add interpreter',
        message: 'Please fill in interpreter name and choose a group'
      })
      return
    }

    if ($scope.newInterpreterSetting.name.indexOf('.') >= 0) {
      BootstrapDialog.alert({
        closable: true,
        title: 'Add interpreter',
        message: '\'.\' is invalid for interpreter name'
      })
      return
    }

    if (_.findIndex($scope.interpreterSettings, {'name': $scope.newInterpreterSetting.name}) >= 0) {
      BootstrapDialog.alert({
        closable: true,
        title: 'Add interpreter',
        message: 'Name ' + $scope.newInterpreterSetting.name + ' already exists'
      })
      return
    }

    let newSetting = $scope.newInterpreterSetting
    if (newSetting.propertyKey !== '' || newSetting.propertyKey) {
      $scope.addNewInterpreterProperty()
    }
    if (newSetting.depArtifact !== '' || newSetting.depArtifact) {
      $scope.addNewInterpreterDependency()
    }
    if (newSetting.option.setPermission === undefined) {
      newSetting.option.setPermission = false
    }
    newSetting.option.owners = angular.element('#newInterpreterOwners').val()

    let request = angular.copy($scope.newInterpreterSetting)

    // Change properties to proper request format
    let newProperties = {}

    for (let p in newSetting.properties) {
      newProperties[p] = {
        value: newSetting.properties[p].value,
        type: newSetting.properties[p].type,
        name: p
      }
    }

    request.properties = newProperties

    $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/setting', request)
      .then(function (res) {
        $scope.resetNewInterpreterSetting()
        getInterpreterSettings()
        $scope.showAddNewSetting = false
        checkDownloadingDependencies()
      }).catch(function (res) {
        const errorMsg = res.data ? res.data.message : 'Could not connect to server.'
        console.log('Error %o %o', res.status, errorMsg)
        ngToast.danger({content: errorMsg, verticalPosition: 'bottom'})
      })
  }

  $scope.cancelInterpreterSetting = function () {
    $scope.showAddNewSetting = false
    $scope.resetNewInterpreterSetting()
  }

  $scope.resetNewInterpreterSetting = function () {
    $scope.newInterpreterSetting = {
      name: undefined,
      group: undefined,
      properties: {},
      dependencies: [],
      option: {
        remote: true,
        isExistingProcess: false,
        setPermission: false,
        session: false,
        process: false

      }
    }
    emptyNewProperty($scope.newInterpreterSetting)
  }

  $scope.removeInterpreterProperty = function (key, settingId) {
    if (settingId === undefined) {
      delete $scope.newInterpreterSetting.properties[key]
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      delete $scope.interpreterSettings[index].properties[key]
    }
  }

  $scope.removeInterpreterDependency = function (artifact, settingId) {
    if (settingId === undefined) {
      $scope.newInterpreterSetting.dependencies = _.reject($scope.newInterpreterSetting.dependencies,
        function (el) {
          return el.groupArtifactVersion === artifact
        })
    } else {
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      $scope.interpreterSettings[index].dependencies = _.reject($scope.interpreterSettings[index].dependencies,
        function (el) {
          return el.groupArtifactVersion === artifact
        })
    }
  }

  $scope.addNewInterpreterProperty = function (settingId) {
    if (settingId === undefined) {
      // Add new property from create form
      if (!$scope.newInterpreterSetting.propertyKey || $scope.newInterpreterSetting.propertyKey === '') {
        return
      }
      $scope.newInterpreterSetting.properties[$scope.newInterpreterSetting.propertyKey] = {
        value: $scope.newInterpreterSetting.propertyValue,
        type: $scope.newInterpreterSetting.propertyType
      }
      emptyNewProperty($scope.newInterpreterSetting)
    } else {
      // Add new property from edit form
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]

      if (!setting.propertyKey || setting.propertyKey === '') {
        return
      }

      setting.properties[setting.propertyKey] =
        {value: setting.propertyValue, type: setting.propertyType}

      emptyNewProperty(setting)
    }
  }

  $scope.addNewInterpreterDependency = function (settingId) {
    if (settingId === undefined) {
      // Add new dependency from create form
      if (!$scope.newInterpreterSetting.depArtifact || $scope.newInterpreterSetting.depArtifact === '') {
        return
      }

      // overwrite if artifact already exists
      let newSetting = $scope.newInterpreterSetting
      for (let d in newSetting.dependencies) {
        if (newSetting.dependencies[d].groupArtifactVersion === newSetting.depArtifact) {
          newSetting.dependencies[d] = {
            'groupArtifactVersion': newSetting.depArtifact,
            'exclusions': newSetting.depExclude
          }
          newSetting.dependencies.splice(d, 1)
        }
      }

      newSetting.dependencies.push({
        'groupArtifactVersion': newSetting.depArtifact,
        'exclusions': (newSetting.depExclude === '') ? [] : newSetting.depExclude
      })
      emptyNewDependency(newSetting)
    } else {
      // Add new dependency from edit form
      let index = _.findIndex($scope.interpreterSettings, {'id': settingId})
      let setting = $scope.interpreterSettings[index]
      if (!setting.depArtifact || setting.depArtifact === '') {
        return
      }

      // overwrite if artifact already exists
      for (let dep in setting.dependencies) {
        if (setting.dependencies[dep].groupArtifactVersion === setting.depArtifact) {
          setting.dependencies[dep] = {
            'groupArtifactVersion': setting.depArtifact,
            'exclusions': setting.depExclude
          }
          setting.dependencies.splice(dep, 1)
        }
      }

      setting.dependencies.push({
        'groupArtifactVersion': setting.depArtifact,
        'exclusions': (setting.depExclude === '') ? [] : setting.depExclude
      })
      emptyNewDependency(setting)
    }
  }

  $scope.resetNewRepositorySetting = function () {
    $scope.newRepoSetting = {
      id: '',
      url: '',
      snapshot: false,
      username: '',
      password: '',
      proxyProtocol: 'HTTP',
      proxyHost: '',
      proxyPort: null,
      proxyLogin: '',
      proxyPassword: ''
    }
  }

  let getRepositories = function () {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/repository')
      .success(function (data, status, headers, config) {
        $scope.repositories = data.body
      }).error(function (data, status, headers, config) {
        console.log('Error %o %o', status, data.message)
      })
  }

  $scope.addNewRepository = function () {
    let request = angular.copy($scope.newRepoSetting)

    $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/repository', request)
      .then(function (res) {
        getRepositories()
        $scope.resetNewRepositorySetting()
        angular.element('#repoModal').modal('hide')
      }).catch(function (res) {
        console.log('Error %o %o', res.headers, res.config)
      })
  }

  $scope.removeRepository = function (repoId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: 'Do you want to delete this repository?',
      callback: function (result) {
        if (result) {
          $http.delete(baseUrlSrv.getRestApiBase() + '/interpreter/repository/' + repoId)
            .then(function (res) {
              let index = _.findIndex($scope.repositories, {'id': repoId})
              $scope.repositories.splice(index, 1)
            }).catch(function (res) {
              console.log('Error %o %o', res.status, res.data ? res.data.message : '')
            })
        }
      }
    })
  }

  $scope.isDefaultRepository = function (repoId) {
    if (repoId === 'central' || repoId === 'local') {
      return true
    } else {
      return false
    }
  }

  $scope.showErrorMessage = function (setting) {
    BootstrapDialog.show({
      title: 'Error downloading dependencies',
      message: setting.errorReason
    })
  }

  let init = function() {
    getAvailableInterpreterPropertyWidgets()

    $scope.resetNewInterpreterSetting()
    $scope.resetNewRepositorySetting()

    getInterpreterSettings()
    getAvailableInterpreters()
    getRepositories()
  }

  $scope.showSparkUI = function (settingId) {
    $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/metadata/' + settingId)
      .then(function (res) {
        if (res.data.body === undefined) {
          BootstrapDialog.alert({
            message: 'No spark application running'
          })
          return
        }
        if (res.data.body.url) {
          window.open(res.data.body.url, '_blank')
        } else {
          BootstrapDialog.alert({
            message: res.data.body.message
          })
        }
      }).catch(function (res) {
        console.log('Error %o %o', res.status, res.data ? res.data.message : '')
      })
  }

  $scope.getInterpreterBindingModeDocsLink = function() {
    const currentVersion = $rootScope.zeppelinVersion
    return `https://zeppelin.apache.org/docs/${currentVersion}/usage/interpreter/interpreter_binding_mode.html`
  }

  init()
}
