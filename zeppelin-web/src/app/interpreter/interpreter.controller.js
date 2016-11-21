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
(function() {

  angular.module('zeppelinWebApp').controller('InterpreterCtrl', InterpreterCtrl);

  InterpreterCtrl.$inject = ['$rootScope', '$scope', '$http', 'baseUrlSrv', 'ngToast', '$timeout', '$route'];

  function InterpreterCtrl($rootScope, $scope, $http, baseUrlSrv, ngToast, $timeout, $route) {
    var interpreterSettingsTmp = [];
    $scope.interpreterSettings = [];
    $scope.availableInterpreters = {};
    $scope.showAddNewSetting = false;
    $scope.showRepositoryInfo = false;
    $scope.searchInterpreter = '';
    $scope._ = _;
    ngToast.dismiss();

    $scope.openPermissions = function() {
      $scope.showInterpreterAuth = true;
    };

    $scope.closePermissions = function() {
      $scope.showInterpreterAuth = false;
    };

    var getSelectJson = function() {
      var selectJson = {
        tags: false,
        multiple: true,
        tokenSeparators: [',', ' '],
        minimumInputLength: 2,
        ajax: {
          url: function(params) {
            if (!params.term) {
              return false;
            }
            return baseUrlSrv.getRestApiBase() + '/security/userlist/' + params.term;
          },
          delay: 250,
          processResults: function(data, params) {
            var users = [];
            if (data.body.users.length !== 0) {
              for (var i = 0; i < data.body.users.length; i++) {
                users.push({
                  'id': data.body.users[i],
                  'text': data.body.users[i]
                });
              }
            }
            return {
              results: users,
              pagination: {
                more: false
              }
            };
          },
          cache: false
        }
      };
      return selectJson;
    };

    $scope.togglePermissions = function(intpName) {
      angular.element('#' + intpName + 'Users').select2(getSelectJson());
      if ($scope.showInterpreterAuth) {
        $scope.closePermissions();
      } else {
        $scope.openPermissions();
      }
    };

    $scope.$on('ngRenderFinished', function(event, data) {
      for (var setting = 0; setting < $scope.interpreterSettings.length; setting++) {
        angular.element('#' + $scope.interpreterSettings[setting].name + 'Users').select2(getSelectJson());
      }
    });

    var getInterpreterSettings = function() {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/setting')
      .success(function(data, status, headers, config) {
        $scope.interpreterSettings = data.body;
        checkDownloadingDependencies();
      }).error(function(data, status, headers, config) {
        if (status === 401) {
          ngToast.danger({
            content: 'You don\'t have permission on this page',
            verticalPosition: 'bottom',
            timeout: '3000'
          });
          setTimeout(function() {
            window.location.replace('/');
          }, 3000);
        }
        console.log('Error %o %o', status, data.message);
      });
    };

    var checkDownloadingDependencies = function() {
      var isDownloading = false;
      for (var index = 0; index < $scope.interpreterSettings.length; index++) {
        var setting = $scope.interpreterSettings[index];
        if (setting.status === 'DOWNLOADING_DEPENDENCIES') {
          isDownloading = true;
        }

        if (setting.status === 'ERROR' || setting.errorReason) {
          ngToast.danger({content: 'Error setting properties for interpreter \'' +
            setting.group + '.' + setting.name + '\': ' + setting.errorReason,
            verticalPosition: 'top', dismissOnTimeout: false});
        }
      }

      if (isDownloading) {
        $timeout(function() {
          if ($route.current.$$route.originalPath === '/interpreter') {
            getInterpreterSettings();
          }
        }, 2000);
      }
    };

    var getAvailableInterpreters = function() {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter').success(function(data, status, headers, config) {
        $scope.availableInterpreters = data.body;
      }).error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
    };

    var emptyNewProperty = function(object) {
      angular.extend(object, {propertyValue: '', propertyKey: ''});
    };

    var emptyNewDependency = function(object) {
      angular.extend(object, {depArtifact: '', depExclude: ''});
    };

    var removeTMPSettings = function(index) {
      interpreterSettingsTmp.splice(index, 1);
    };

    $scope.copyOriginInterpreterSettingProperties = function(settingId) {
      var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
      interpreterSettingsTmp[index] = angular.copy($scope.interpreterSettings[index]);
    };

    $scope.setPerNoteOption = function(settingId, sessionOption) {
      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }

      if (sessionOption === 'isolated') {
        option.perNote = sessionOption;
        option.session = false;
        option.process = true;
      } else if (sessionOption === 'scoped') {
        option.perNote = sessionOption;
        option.session = true;
        option.process = false;
      } else {
        option.perNote = 'shared';
        option.session = false;
        option.process = false;
      }
    };

    $scope.setPerUserOption = function(settingId, sessionOption) {
      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }

      if (sessionOption === 'isolated') {
        option.perUser = sessionOption;
        option.session = false;
        option.process = true;
      } else if (sessionOption === 'scoped') {
        option.perUser = sessionOption;
        option.session = true;
        option.process = false;
      } else {
        option.perUser = 'shared';
        option.session = false;
        option.process = false;
      }
    };

    $scope.getPerNoteOption = function(settingId) {
      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }

      if (option.perNote === 'scoped') {
        return 'scoped';
      } else if (option.perNote === 'isolated') {
        return 'isolated';
      } else {
        return 'shared';
      }
    };

    $scope.getPerUserOption = function(settingId) {
      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }

      if (option.perUser === 'scoped') {
        return 'scoped';
      } else if (option.perUser === 'isolated') {
        return 'isolated';
      } else {
        return 'shared';
      }
    };

    $scope.getInterpreterRunningOption = function(settingId) {
      var sharedModeName = 'shared';

      var globallyModeName = 'Globally';
      var perNoteModeName = 'Per Note';
      var perUserModeName = 'Per User';

      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }

      var perNote = option.perNote;
      var perUser = option.perUser;

      // Globally == shared_perNote + shared_perUser
      if (perNote === sharedModeName && perUser === sharedModeName) {
        return globallyModeName;
      }

      if ($rootScope.ticket.ticket === 'anonymous' && $rootScope.ticket.roles === '[]') {
        if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
          return perNoteModeName;
        }
      } else if ($rootScope.ticket.ticket !== 'anonymous') {
        if (perNote !== undefined && typeof perNote === 'string' && perNote !== '') {
          if (perUser !== undefined && typeof perUser === 'string' && perUser !== '') {
            return perUserModeName;
          }
          return perNoteModeName;
        }
      }

      option.perNote = sharedModeName;
      option.perUser = sharedModeName;
      return globallyModeName;
    };

    $scope.setInterpreterRunningOption = function(settingId, isPerNoteMode, isPerUserMode) {
      var option;
      if (settingId === undefined) {
        option = $scope.newInterpreterSetting.option;
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        option = setting.option;
      }
      option.perNote = isPerNoteMode;
      option.perUser = isPerUserMode;
    };

    $scope.updateInterpreterSetting = function(form, settingId) {
      var thisConfirm = BootstrapDialog.confirm({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: '',
        message: 'Do you want to update this interpreter and restart with new settings?',
        callback: function(result) {
          if (result) {
            var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
            var setting = $scope.interpreterSettings[index];
            if (setting.propertyKey !== '' || setting.propertyKey) {
              $scope.addNewInterpreterProperty(settingId);
            }
            if (setting.depArtifact !== '' || setting.depArtifact) {
              $scope.addNewInterpreterDependency(settingId);
            }
            // add missing field of option
            if (!setting.option) {
              setting.option = {};
            }
            if (setting.option.isExistingProcess === undefined) {
              setting.option.isExistingProcess = false;
            }
            if (setting.option.setPermission === undefined) {
              setting.option.setPermission = false;
            }
            if (setting.option.isUserImpersonate === undefined) {
              setting.option.isUserImpersonate = false;
            }
            if (!($scope.getInterpreterRunningOption(settingId) === 'Per User' &&
                $scope.getPerUserOption(settingId) === 'isolated')) {
              setting.option.isUserImpersonate = false;
            }
            if (setting.option.remote === undefined) {
              // remote always true for now
              setting.option.remote = true;
            }
            setting.option.users = angular.element('#' + setting.name + 'Users').val();

            var request = {
              option: angular.copy(setting.option),
              properties: angular.copy(setting.properties),
              dependencies: angular.copy(setting.dependencies)
            };

            thisConfirm.$modalFooter.find('button').addClass('disabled');
            thisConfirm.$modalFooter.find('button:contains("OK")')
              .html('<i class="fa fa-circle-o-notch fa-spin"></i> Saving Setting');

            $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId, request)
              .success(function(data, status, headers, config) {
                $scope.interpreterSettings[index] = data.body;
                removeTMPSettings(index);
                checkDownloadingDependencies();
                thisConfirm.close();
              })
              .error(function(data, status, headers, config) {
                console.log('Error %o %o', status, data.message);
                ngToast.danger({content: data.message, verticalPosition: 'bottom'});
                form.$show();
                thisConfirm.close();
              });
            return false;
          } else {
            form.$show();
          }
        }
      });
    };

    $scope.resetInterpreterSetting = function(settingId) {
      var index = _.findIndex($scope.interpreterSettings, {'id': settingId});

      // Set the old settings back
      $scope.interpreterSettings[index] = angular.copy(interpreterSettingsTmp[index]);
      removeTMPSettings(index);
    };

    $scope.removeInterpreterSetting = function(settingId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to delete this interpreter setting?',
        callback: function(result) {
          if (result) {
            $http.delete(baseUrlSrv.getRestApiBase() + '/interpreter/setting/' + settingId)
              .success(function(data, status, headers, config) {

                var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
                $scope.interpreterSettings.splice(index, 1);
              }).error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
          }
        }
      });
    };

    $scope.newInterpreterGroupChange = function() {
      var el = _.pluck(_.filter($scope.availableInterpreters, {'name': $scope.newInterpreterSetting.group}),
        'properties');
      var properties = {};
      for (var i = 0; i < el.length; i++) {
        var intpInfo = el[i];
        for (var key in intpInfo) {
          properties[key] = {
            value: intpInfo[key].defaultValue,
            description: intpInfo[key].description
          };
        }
      }
      $scope.newInterpreterSetting.properties = properties;
    };

    $scope.restartInterpreterSetting = function(settingId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to restart this interpreter?',
        callback: function(result) {
          if (result) {
            $http.put(baseUrlSrv.getRestApiBase() + '/interpreter/setting/restart/' + settingId)
              .success(function(data, status, headers, config) {
                var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
                $scope.interpreterSettings[index] = data.body;
              }).error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
          }
        }
      });
    };

    $scope.addNewInterpreterSetting = function() {
      //user input validation on interpreter creation
      if (!$scope.newInterpreterSetting.name ||
          !$scope.newInterpreterSetting.name.trim() || !$scope.newInterpreterSetting.group) {
        BootstrapDialog.alert({
          closable: true,
          title: 'Add interpreter',
          message: 'Please fill in interpreter name and choose a group'
        });
        return;
      }

      if ($scope.newInterpreterSetting.name.indexOf('.') >= 0) {
        BootstrapDialog.alert({
          closable: true,
          title: 'Add interpreter',
          message: '\'.\' is invalid for interpreter name'
        });
        return;
      }

      if (_.findIndex($scope.interpreterSettings, {'name': $scope.newInterpreterSetting.name}) >= 0) {
        BootstrapDialog.alert({
          closable: true,
          title: 'Add interpreter',
          message: 'Name ' + $scope.newInterpreterSetting.name + ' already exists'
        });
        return;
      }

      var newSetting = $scope.newInterpreterSetting;
      if (newSetting.propertyKey !== '' || newSetting.propertyKey) {
        $scope.addNewInterpreterProperty();
      }
      if (newSetting.depArtifact !== '' || newSetting.depArtifact) {
        $scope.addNewInterpreterDependency();
      }
      if (newSetting.option.setPermission === undefined) {
        newSetting.option.setPermission = false;
      }
      newSetting.option.users = angular.element('#newInterpreterUsers').val();

      var request = angular.copy($scope.newInterpreterSetting);

      // Change properties to proper request format
      var newProperties = {};
      for (var p in newSetting.properties) {
        newProperties[p] = newSetting.properties[p].value;
      }
      request.properties = newProperties;

      $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/setting', request)
        .success(function(data, status, headers, config) {
          $scope.resetNewInterpreterSetting();
          getInterpreterSettings();
          $scope.showAddNewSetting = false;
          checkDownloadingDependencies();
        }).error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
        ngToast.danger({content: data.message, verticalPosition: 'bottom'});
      });
    };

    $scope.cancelInterpreterSetting = function() {
      $scope.showAddNewSetting = false;
      $scope.resetNewInterpreterSetting();
    };

    $scope.resetNewInterpreterSetting = function() {
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
      };
      emptyNewProperty($scope.newInterpreterSetting);
    };

    $scope.removeInterpreterProperty = function(key, settingId) {
      if (settingId === undefined) {
        delete $scope.newInterpreterSetting.properties[key];
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        delete $scope.interpreterSettings[index].properties[key];
      }
    };

    $scope.removeInterpreterDependency = function(artifact, settingId) {
      if (settingId === undefined) {
        $scope.newInterpreterSetting.dependencies = _.reject($scope.newInterpreterSetting.dependencies,
          function(el) {
            return el.groupArtifactVersion === artifact;
          });
      } else {
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        $scope.interpreterSettings[index].dependencies = _.reject($scope.interpreterSettings[index].dependencies,
          function(el) {
            return el.groupArtifactVersion === artifact;
          });
      }
    };

    $scope.addNewInterpreterProperty = function(settingId) {
      if (settingId === undefined) {
        // Add new property from create form
        if (!$scope.newInterpreterSetting.propertyKey || $scope.newInterpreterSetting.propertyKey === '') {
          return;
        }

        $scope.newInterpreterSetting.properties[$scope.newInterpreterSetting.propertyKey] = {
          value: $scope.newInterpreterSetting.propertyValue
        };
        emptyNewProperty($scope.newInterpreterSetting);
      } else {
        // Add new property from edit form
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];

        if (!setting.propertyKey || setting.propertyKey === '') {
          return;
        }
        setting.properties[setting.propertyKey] = setting.propertyValue;
        emptyNewProperty(setting);
      }
    };

    $scope.addNewInterpreterDependency = function(settingId) {
      if (settingId === undefined) {
        // Add new dependency from create form
        if (!$scope.newInterpreterSetting.depArtifact || $scope.newInterpreterSetting.depArtifact === '') {
          return;
        }

        // overwrite if artifact already exists
        var newSetting = $scope.newInterpreterSetting;
        for (var d in newSetting.dependencies) {
          if (newSetting.dependencies[d].groupArtifactVersion === newSetting.depArtifact) {
            newSetting.dependencies[d] = {
              'groupArtifactVersion': newSetting.depArtifact,
              'exclusions': newSetting.depExclude
            };
            newSetting.dependencies.splice(d, 1);
          }
        }

        newSetting.dependencies.push({
          'groupArtifactVersion': newSetting.depArtifact,
          'exclusions': (newSetting.depExclude === '') ? [] : newSetting.depExclude
        });
        emptyNewDependency(newSetting);
      } else {
        // Add new dependency from edit form
        var index = _.findIndex($scope.interpreterSettings, {'id': settingId});
        var setting = $scope.interpreterSettings[index];
        if (!setting.depArtifact || setting.depArtifact === '') {
          return;
        }

        // overwrite if artifact already exists
        for (var dep in setting.dependencies) {
          if (setting.dependencies[dep].groupArtifactVersion === setting.depArtifact) {
            setting.dependencies[dep] = {
              'groupArtifactVersion': setting.depArtifact,
              'exclusions': setting.depExclude
            };
            setting.dependencies.splice(dep, 1);
          }
        }

        setting.dependencies.push({
          'groupArtifactVersion': setting.depArtifact,
          'exclusions': (setting.depExclude === '') ? [] : setting.depExclude
        });
        emptyNewDependency(setting);
      }
    };

    $scope.resetNewRepositorySetting = function() {
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
      };
    };

    var getRepositories = function() {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/repository')
        .success(function(data, status, headers, config) {
          $scope.repositories = data.body;
        }).error(function(data, status, headers, config) {
        console.log('Error %o %o', status, data.message);
      });
    };

    $scope.addNewRepository = function() {
      var request = angular.copy($scope.newRepoSetting);

      $http.post(baseUrlSrv.getRestApiBase() + '/interpreter/repository', request)
        .success(function(data, status, headers, config) {
          getRepositories();
          $scope.resetNewRepositorySetting();
          angular.element('#repoModal').modal('hide');
        }).error(function(data, status, headers, config) {
        console.log('Error %o %o', headers, config);
      });
    };

    $scope.removeRepository = function(repoId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Do you want to delete this repository?',
        callback: function(result) {
          if (result) {
            $http.delete(baseUrlSrv.getRestApiBase() + '/interpreter/repository/' + repoId)
              .success(function(data, status, headers, config) {
                var index = _.findIndex($scope.repositories, {'id': repoId});
                $scope.repositories.splice(index, 1);
              }).error(function(data, status, headers, config) {
              console.log('Error %o %o', status, data.message);
            });
          }
        }
      });
    };

    $scope.isDefaultRepository = function(repoId) {
      if (repoId === 'central' || repoId === 'local') {
        return true;
      } else {
        return false;
      }
    };

    $scope.showErrorMessage = function(setting) {
      BootstrapDialog.show({
        title: 'Error downloading dependencies',
        message: setting.errorReason
      });
    };

    var init = function() {
      $scope.resetNewInterpreterSetting();
      $scope.resetNewRepositorySetting();

      getInterpreterSettings();
      getAvailableInterpreters();
      getRepositories();
    };

    $scope.showSparkUI = function(settingId) {
      $http.get(baseUrlSrv.getRestApiBase() + '/interpreter/getmetainfos/' + settingId + '?propName=url')
        .success(function(data, status, headers, config) {
          var url = data.body.url;
          if (!url) {
            BootstrapDialog.alert({
              message: 'No spark application running'
            });
            return;
          }
          window.open(url, '_blank');
        }).error(function(data, status, headers, config) {
         console.log('Error %o %o', status, data.message);
       });
    };

    init();
  }

})();
