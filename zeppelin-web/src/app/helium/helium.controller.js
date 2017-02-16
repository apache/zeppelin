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

import { HeliumType, } from '../../components/helium/helium-type';

export default function HeliumCtrl($scope, $rootScope, $sce,
                                   baseUrlSrv, ngToast, heliumService) {
  'ngInject';

  $scope.pkgSearchResults = {};
  $scope.defaultPackages = {};
  $scope.showVersions = {};
  $scope.bundleOrder = [];
  $scope.bundleOrderChanged = false;
  $scope.defaultPackageConfigs = {}; // { pkgName, [{name, type, desc, value, defaultValue}] }

  function init() {
    // get all package info and set config
    heliumService.getAllPackageInfoAndDefaultPackages()
      .then(({ pkgSearchResults, defaultPackages }) => {
        $scope.pkgSearchResults = pkgSearchResults;
        $scope.defaultPackages = defaultPackages;
        return heliumService.getAllPackageConfigs()
      })
      .then(defaultPackageConfigs => {
        $scope.defaultPackageConfigs = defaultPackageConfigs;
      });

    // 2. get vis package order
    heliumService.getVisualizationPackageOrder()
      .then(visPackageOrder => {
        $scope.bundleOrder = visPackageOrder;
        $scope.bundleOrderChanged = false;
      });
  }

  $scope.bundleOrderListeners = {
    accept: function(sourceItemHandleScope, destSortableScope) {return true;},
    itemMoved: function(event) {},
    orderChanged: function(event) {
      $scope.bundleOrderChanged = true;
    }
  };

  $scope.saveBundleOrder = function() {
    var confirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Save changes?',
      callback: function(result) {
        if (result) {
          confirm.$modalFooter.find('button').addClass('disabled');
          confirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Enabling');
          heliumService.setVisualizationPackageOrder($scope.bundleOrder).
          success(function(data, status) {
            init();
            confirm.close();
          }).
          error(function(data, status) {
            confirm.close();
            console.log('Failed to save order');
            BootstrapDialog.show({
              title: 'Error on saving order ',
              message: data.message
            });
          });
          return false;
        }
      }
    });
  };

  var getLicense = function(name, artifact) {
    var filteredPkgSearchResults = _.filter($scope.defaultPackages[name], function(p) {
      return p.artifact === artifact;
    });

    var license;
    if (filteredPkgSearchResults.length === 0) {
      filteredPkgSearchResults = _.filter($scope.pkgSearchResults[name], function(p) {
        return p.pkg.artifact === artifact;
      });

      if (filteredPkgSearchResults.length > 0) {
        license  = filteredPkgSearchResults[0].pkg.license;
      }
    } else {
      license = filteredPkgSearchResults[0].license;
    }

    if (!license) {
      license = 'Unknown';
    }
    return license;
  }

  $scope.enable = function(name, artifact) {
    var license = getLicense(name, artifact);

    var confirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to enable ' + name + '?' +
      '<div style="color:gray">' + artifact + '</div>' +
      '<div style="border-top: 1px solid #efefef; margin-top: 10px; padding-top: 5px;">License</div>' +
      '<div style="color:gray">' + license + '</div>',
      callback: function(result) {
        if (result) {
          confirm.$modalFooter.find('button').addClass('disabled');
          confirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Enabling');
          heliumService.enable(name, artifact).
          success(function(data, status) {
            init();
            confirm.close();
          }).
          error(function(data, status) {
            confirm.close();
            console.log('Failed to enable package %o %o. %o', name, artifact, data);
            BootstrapDialog.show({
              title: 'Error on enabling ' + name,
              message: data.message
            });
          });
          return false;
        }
      }
    });
  };

  $scope.disable = function(name) {
    var confirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Do you want to disable ' + name + '?',
      callback: function(result) {
        if (result) {
          confirm.$modalFooter.find('button').addClass('disabled');
          confirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Disabling');
          heliumService.disable(name).
          success(function(data, status) {
            init();
            confirm.close();
          }).
          error(function(data, status) {
            confirm.close();
            console.log('Failed to disable package %o. %o', name, data);
            BootstrapDialog.show({
              title: 'Error on disabling ' + name,
              message: data.message
            });
          });
          return false;
        }
      }
    });
  };

  $scope.toggleVersions = function(pkgName) {
    if ($scope.showVersions[pkgName]) {
      $scope.showVersions[pkgName] = false;
    } else {
      $scope.showVersions[pkgName] = true;
    }
  };

  $scope.isLocalPackage = function(pkgSearchResult) {
    const pkg = pkgSearchResult.pkg;
    return pkg.artifact && !pkg.artifact.includes('@');
  };

  $scope.hasNpmLink = function(pkgSearchResult) {
    const pkg = pkgSearchResult.pkg;
    return (pkg.type === HeliumType.SPELL || pkg.type === HeliumType.VISUALIZATION) &&
      !$scope.isLocalPackage(pkgSearchResult);
  };

  $scope.configExists = function(pkgSearchResult) {
    // helium package config is persisted per version
    return pkgSearchResult.pkg.config && pkgSearchResult.pkg.artifact;
  };

  $scope.configOpened = function(pkgSearchResult) {
    return pkgSearchResult.configOpened && !pkgSearchResult.configFetching;
  };

  $scope.getConfigButtonClass = function(pkgSearchResult) {
    return (pkgSearchResult.configOpened && pkgSearchResult.configFetching) ?
      'disabled' : '';
  }

  $scope.toggleConfigButton = function(pkgSearchResult) {
    if (pkgSearchResult.configOpened) {
      pkgSearchResult.configOpened = false;
      return;
    }

    const pkg = pkgSearchResult.pkg;
    const pkgName = pkg.name;
    pkgSearchResult.configFetching = true;
    pkgSearchResult.configOpened = true;

    heliumService.getSinglePackageConfigs(pkg)
      .then(confs => {
        $scope.defaultPackageConfigs[pkgName] = confs;
        pkgSearchResult.configFetching = false;
      });
  };

  $scope.saveConfig = function(pkgSearchResult) {
    const pkgName = pkgSearchResult.pkg.name;
    const currentConf = $scope.defaultPackageConfigs[pkgName];

    heliumService.saveConfig(pkgSearchResult.pkg, currentConf);
  };

  init();
}
