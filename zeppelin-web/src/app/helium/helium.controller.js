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
    $scope.visualizationOrder = [];
    $scope.visualizationOrderChanged = false;

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

    var getVisualizationOrder = function() {
      heliumService.getVisualizationOrder().
        success(function(data, status) {
          $scope.visualizationOrder = data.body;
        }).
        error(function(data, status) {
          console.log('Can not get visualization order %o %o', status, data);
        });
    };

    $scope.visualizationOrderListeners = {
      accept: function(sourceItemHandleScope, destSortableScope) {return true;},
      itemMoved: function(event) {},
      orderChanged: function(event) {
        $scope.visualizationOrderChanged = true;
      }
    };

    var init = function() {
      getAllPackageInfo();
      getVisualizationOrder();
      $scope.visualizationOrderChanged = false;
    };

    init();

    $scope.saveVisualizationOrder = function() {
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
            heliumService.setVisualizationOrder($scope.visualizationOrder).
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

    $scope.enable = function(name, artifact) {
      var confirm = BootstrapDialog.confirm({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: '',
        message: 'Enable ' + name + '? <div style="color:gray">' + artifact + '</div>',
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
        message: 'Disable ' + name + '?',
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
