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

import { HeliumType, } from './helium-type'

export default function HeliumCtrl ($scope, $rootScope, $sce,
                                   baseUrlSrv, ngToast, heliumService) {
  'ngInject'

  $scope.pkgSearchResults = {}
  $scope.defaultPackages = {}
  $scope.showVersions = {}
  $scope.bundleOrder = []
  $scope.bundleOrderChanged = false
  $scope.vizTypePkg = {}
  $scope.spellTypePkg = {}
  $scope.intpTypePkg = {}
  $scope.appTypePkg = {}
  $scope.numberOfEachPackageByType = {}
  $scope.allPackageTypes = [HeliumType][0]
  $scope.pkgListByType = 'VISUALIZATION'
  $scope.defaultPackageConfigs = {} // { pkgName, [{name, type, desc, value, defaultValue}] }
  $scope.intpDefaultIcon = $sce.trustAsHtml('<img src="../assets/images/maven_default_icon.png" style="width: 12px"/>')

  function init () {
    // get all package info and set config
    heliumService.getAllPackageInfoAndDefaultPackages()
      .then(({ pkgSearchResults, defaultPackages }) => {
        // pagination
        $scope.itemsPerPage = 10
        $scope.currentPage = 1
        $scope.maxSize = 5

        $scope.pkgSearchResults = pkgSearchResults
        $scope.defaultPackages = defaultPackages
        classifyPkgType($scope.defaultPackages)

        return heliumService.getAllPackageConfigs()
      })
      .then(defaultPackageConfigs => {
        $scope.defaultPackageConfigs = defaultPackageConfigs
        return heliumService.getVisualizationPackageOrder()
      })
      .then(visPackageOrder => {
        setVisPackageOrder(visPackageOrder)
      })
  }

  const setVisPackageOrder = function(visPackageOrder) {
    $scope.bundleOrder = visPackageOrder
    $scope.bundleOrderChanged = false
  }

  let orderPackageByPubDate = function (a, b) {
    if (!a.pkg.published) {
      // Because local registry pkgs don't have 'published' field, put current time instead to show them first
      a.pkg.published = new Date().getTime()
    }

    return new Date(a.pkg.published).getTime() - new Date(b.pkg.published).getTime()
  }

  const classifyPkgType = function (packageInfo) {
    let allTypesOfPkg = {}
    let vizTypePkg = []
    let spellTypePkg = []
    let intpTypePkg = []
    let appTypePkg = []

    let packageInfoArr = Object.keys(packageInfo).map(key => packageInfo[key])
    packageInfoArr = packageInfoArr.sort(orderPackageByPubDate).reverse()

    for (let name in packageInfoArr) {
      let pkgs = packageInfoArr[name]
      let pkgType = pkgs.pkg.type

      switch (pkgType) {
        case HeliumType.VISUALIZATION:
          vizTypePkg.push(pkgs)
          break
        case HeliumType.SPELL:
          spellTypePkg.push(pkgs)
          break
        case HeliumType.INTERPRETER:
          intpTypePkg.push(pkgs)
          break
        case HeliumType.APPLICATION:
          appTypePkg.push(pkgs)
          break
      }
    }

    let pkgsArr = [
      vizTypePkg,
      spellTypePkg,
      intpTypePkg,
      appTypePkg
    ]
    for (let idx in _.keys(HeliumType)) {
      allTypesOfPkg[_.keys(HeliumType)[idx]] = pkgsArr[idx]
    }

    $scope.allTypesOfPkg = allTypesOfPkg
  }

  $scope.bundleOrderListeners = {
    accept: function (sourceItemHandleScope, destSortableScope) { return true },
    itemMoved: function (event) {},
    orderChanged: function (event) {
      $scope.bundleOrderChanged = true
    }
  }

  $scope.saveBundleOrder = function () {
    const confirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '',
      message: 'Save changes?',
      callback: function (result) {
        if (result) {
          confirm.$modalFooter.find('button').addClass('disabled')
          confirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Enabling')
          heliumService.setVisualizationPackageOrder($scope.bundleOrder)
            .success(function (data, status) {
              setVisPackageOrder($scope.bundleOrder)
              confirm.close()
            })
            .error(function (data, status) {
              confirm.close()
              console.log('Failed to save order')
              BootstrapDialog.show({
                title: 'Error on saving order ',
                message: data.message
              })
            })
          return false
        }
      }
    })
  }

  let getLicense = function (name, artifact) {
    let filteredPkgSearchResults = _.filter($scope.defaultPackages[name], function (p) {
      return p.artifact === artifact
    })

    let license
    if (filteredPkgSearchResults.length === 0) {
      filteredPkgSearchResults = _.filter($scope.pkgSearchResults[name], function (p) {
        return p.pkg.artifact === artifact
      })

      if (filteredPkgSearchResults.length > 0) {
        license = filteredPkgSearchResults[0].pkg.license
      }
    } else {
      license = filteredPkgSearchResults[0].license
    }

    if (!license) {
      license = 'Unknown'
    }
    return license
  }

  const getHeliumTypeText = function (type) {
    if (type === HeliumType.VISUALIZATION) {
      return `<a target="_blank" href="https://zeppelin.apache.org/docs/${$rootScope.zeppelinVersion}/development/helium/writing_visualization.html">${type}</a>` // eslint-disable-line max-len
    } else if (type === HeliumType.SPELL) {
      return `<a target="_blank" href="https://zeppelin.apache.org/docs/${$rootScope.zeppelinVersion}/development/helium/writing_spell.html">${type}</a>` // eslint-disable-line max-len
    } else {
      return type
    }
  }

  $scope.enable = function (name, artifact, type, groupId, description) {
    let license = getLicense(name, artifact)
    let mavenArtifactInfoToHTML = groupId + ':' + artifact.split('@')[0] + ':' + artifact.split('@')[1]
    let zeppelinVersion = $rootScope.zeppelinVersion
    let url = 'https://zeppelin.apache.org/docs/' + zeppelinVersion + '/manual/interpreterinstallation.html'

    let confirm = ''
    if (type === HeliumType.INTERPRETER) {
      confirm = BootstrapDialog.show({
        title: '',
        message: '<p>Below command will download maven artifact ' +
        '<code style="font-size: 11.5px; background-color: #f5f5f5; color: #0a0a0a">' +
        mavenArtifactInfoToHTML + '</code>' +
        ' and all of its transitive dependencies into interpreter/interpreter-name directory.<p>' +
        '<div class="highlight"><pre><code class="text language-text" data-lang="text" style="font-size: 11.5px">' +
        './bin/install-interpreter.sh --name "interpreter-name" --artifact ' +
        mavenArtifactInfoToHTML + ' </code></pre>' +
        '<p>After restart Zeppelin, create interpreter setting and bind it with your note. ' +
        'For more detailed information, see <a target="_blank" href=' +
        url + '>Interpreter Installation.</a></p>'
      })
    } else {
      confirm = BootstrapDialog.confirm({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: '<div style="font-weight: 300;">Do you want to enable Helium Package?</div>',
        message:
          '<div style="font-size: 14px; margin-top: 5px;">Artifact</div>' +
          `<div style="color:gray">${artifact}</div>` +
          '<hr style="margin-top: 10px; margin-bottom: 10px;" />' +
          '<div style="font-size: 14px; margin-bottom: 2px;">Type</div>' +
          `<div style="color:gray">${getHeliumTypeText(type)}</div>` +
          '<hr style="margin-top: 10px; margin-bottom: 10px;" />' +
          '<div style="font-size: 14px;">Description</div>' +
          `<div style="color:gray">${description}</div>` +
          '<hr style="margin-top: 10px; margin-bottom: 10px;" />' +
          '<div style="font-size: 14px;">License</div>' +
          `<div style="color:gray">${license}</div>`,
        callback: function (result) {
          if (result) {
            confirm.$modalFooter.find('button').addClass('disabled')
            confirm.$modalFooter.find('button:contains("OK")')
              .html('<i class="fa fa-circle-o-notch fa-spin"></i> Enabling')
            heliumService.enable(name, artifact, type).success(function (data, status) {
              init()
              confirm.close()
            }).error(function (data, status) {
              confirm.close()
              console.log('Failed to enable package %o %o. %o', name, artifact, data)
              BootstrapDialog.show({
                title: 'Error on enabling ' + name,
                message: data.message
              })
            })
            return false
          }
        }
      })
    }
  }

  $scope.disable = function (name, artifact) {
    const confirm = BootstrapDialog.confirm({
      closable: false,
      closeByBackdrop: false,
      closeByKeyboard: false,
      title: '<div style="font-weight: 300;">Do you want to disable Helium Package?</div>',
      message: artifact,
      callback: function (result) {
        if (result) {
          confirm.$modalFooter.find('button').addClass('disabled')
          confirm.$modalFooter.find('button:contains("OK")')
            .html('<i class="fa fa-circle-o-notch fa-spin"></i> Disabling')
          heliumService.disable(name)
          .success(function (data, status) {
            init()
            confirm.close()
          })
          .error(function (data, status) {
            confirm.close()
            console.log('Failed to disable package %o. %o', name, data)
            BootstrapDialog.show({
              title: 'Error on disabling ' + name,
              message: data.message
            })
          })
          return false
        }
      }
    })
  }

  $scope.toggleVersions = function (pkgName) {
    if ($scope.showVersions[pkgName]) {
      $scope.showVersions[pkgName] = false
    } else {
      $scope.showVersions[pkgName] = true
    }
  }

  $scope.isLocalPackage = function (pkgSearchResult) {
    const pkg = pkgSearchResult.pkg
    return pkg.artifact && !pkg.artifact.includes('@')
  }

  $scope.hasNpmLink = function (pkgSearchResult) {
    const pkg = pkgSearchResult.pkg
    return (pkg.type === HeliumType.SPELL || pkg.type === HeliumType.VISUALIZATION) &&
      !$scope.isLocalPackage(pkgSearchResult)
  }

  $scope.hasMavenLink = function (pkgSearchResult) {
    const pkg = pkgSearchResult.pkg
    return (pkg.type === HeliumType.APPLICATION || pkg.type === HeliumType.INTERPRETER) &&
      !$scope.isLocalPackage(pkgSearchResult)
  }

  $scope.getPackageSize = function (pkgSearchResult, targetPkgType) {
    let result = []
    _.map(pkgSearchResult, function (pkg) {
      result.push(_.find(pkg, {type: targetPkgType}))
    })
    return _.compact(result).length
  }

  $scope.configExists = function (pkgSearchResult) {
    // helium package config is persisted per version
    return pkgSearchResult.pkg.config && pkgSearchResult.pkg.artifact
  }

  $scope.configOpened = function (pkgSearchResult) {
    return pkgSearchResult.configOpened && !pkgSearchResult.configFetching
  }

  $scope.getConfigButtonClass = function (pkgSearchResult) {
    return (pkgSearchResult.configOpened && pkgSearchResult.configFetching)
      ? 'disabled' : ''
  }

  $scope.toggleConfigButton = function (pkgSearchResult) {
    if (pkgSearchResult.configOpened) {
      pkgSearchResult.configOpened = false
      return
    }

    const pkg = pkgSearchResult.pkg
    const pkgName = pkg.name
    pkgSearchResult.configFetching = true
    pkgSearchResult.configOpened = true

    heliumService.getSinglePackageConfigs(pkg)
      .then(confs => {
        $scope.defaultPackageConfigs[pkgName] = confs
        pkgSearchResult.configFetching = false
      })
  }

  $scope.saveConfig = function (pkgSearchResult) {
    const pkgName = pkgSearchResult.pkg.name
    const currentConf = $scope.defaultPackageConfigs[pkgName]

    heliumService.saveConfig(pkgSearchResult.pkg, currentConf, () => {
      // close after config is saved
      pkgSearchResult.configOpened = false
    })
  }

  $scope.getDescriptionText = function (pkgSearchResult) {
    return $sce.trustAsHtml(pkgSearchResult.pkg.description)
  }

  init()
}
