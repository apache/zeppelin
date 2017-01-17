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

(function() {

  angular.module('zeppelinWebApp').controller('HeliumCtrl', HeliumCtrl);

  HeliumCtrl.$inject = ['$scope', '$rootScope', '$sce', 'baseUrlSrv', 'ngToast', 'heliumService'];

  function HeliumCtrl($scope, $rootScope, $sce, baseUrlSrv, ngToast, heliumService) {
    $scope.packageInfos = {};
    $scope.defaultVersions = {};
    $scope.showVersions = {};
    $scope.bundleOrder = [];
    $scope.bundleOrderChanged = false;

    var buildDefaultVersionListToDisplay = function(packageInfos) {
      var defaultVersions = {};
      // show enabled version if any version of package is enabled
      for (var name in packageInfos) {
        var pkgs = packageInfos[name];
        for (var pkgIdx in pkgs) {
          var pkg = pkgs[pkgIdx];
          pkg.pkg.icon = $sce.trustAsHtml(pkg.pkg.icon);
          if (pkg.enabled) {
            defaultVersions[name] = pkg;
            pkgs.splice(pkgIdx, 1);
            break;
          }
        }

        // show first available version if package is not enabled
        if (!defaultVersions[name]) {
          defaultVersions[name] = pkgs[0];
          pkgs.splice(0, 1);
        }
      }
      $scope.defaultVersions = defaultVersions;
    };

    var getAllPackageInfo = function() {
      heliumService.getAllPackageInfo().
        success(function(data, status) {
          $scope.packageInfos = data.body;
          buildDefaultVersionListToDisplay($scope.packageInfos);
        }).
        error(function(data, status) {
          console.log('Can not load package info %o %o', status, data);
        });
    };

    var getBundleOrder = function() {
      heliumService.getBundleOrder().
        success(function(data, status) {
          $scope.bundleOrder = data.body;
        }).
        error(function(data, status) {
          console.log('Can not get bundle order %o %o', status, data);
        });
    };

    $scope.bundleOrderListeners = {
      accept: function(sourceItemHandleScope, destSortableScope) {return true;},
      itemMoved: function(event) {},
      orderChanged: function(event) {
        $scope.bundleOrderChanged = true;
      }
    };

    var init = function() {
      getAllPackageInfo();
      getBundleOrder();
      $scope.bundleOrderChanged = false;
    };

    init();

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
            heliumService.setBundleOrder($scope.bundleOrder).
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
    }

    var getLicense = function(name, artifact) {
      var pkg = _.filter($scope.defaultVersions[name], function(p) {
        return p.artifact === artifact;
      });

      var license;
      if (pkg.length === 0) {
        pkg = _.filter($scope.packageInfos[name], function(p) {
          return p.pkg.artifact === artifact;
        });

        if (pkg.length > 0) {
          license  = pkg[0].pkg.license;
        }
      } else {
        license = pkg[0].license;
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
  }
})();
