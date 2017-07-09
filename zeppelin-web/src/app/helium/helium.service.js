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
import {
  createAllPackageConfigs,
  createPersistableConfig,
  mergePersistedConfWithSpec,
} from './helium-conf'
import {
  createDefaultPackages,
} from './helium-package'

angular.module('zeppelinWebApp').service('heliumService', HeliumService)

export default function HeliumService($http, $sce, baseUrlSrv) {
  'ngInject'

  let visualizationBundles = []
  let visualizationPackageOrder = []
  // name `heliumBundles` should be same as `HeliumBundleFactory.HELIUM_BUNDLES_VAR`
  let heliumBundles = []
  // map for `{ magic: interpreter }`
  let spellPerMagic = {}
  // map for `{ magic: package-name }`
  let pkgNamePerMagic = {}

  /**
   * @param magic {string} e.g `%flowchart`
   * @returns {SpellBase} undefined if magic is not registered
   */
  this.getSpellByMagic = function (magic) {
    return spellPerMagic[magic]
  }

  this.executeSpell = function (magic, textWithoutMagic) {
    const promisedConf = this.getSinglePackageConfigUsingMagic(magic)
      .then(confs => createPersistableConfig(confs))

    return promisedConf.then(conf => {
      const spell = this.getSpellByMagic(magic)
      const spellResult = spell.interpret(textWithoutMagic, conf)
      const parsed = spellResult.getAllParsedDataWithTypes(
        spellPerMagic, magic, textWithoutMagic)

      return parsed
    })
  }

  this.executeSpellAsDisplaySystem = function (magic, textWithoutMagic) {
    const promisedConf = this.getSinglePackageConfigUsingMagic(magic)
      .then(confs => createPersistableConfig(confs))

    return promisedConf.then(conf => {
      const spell = this.getSpellByMagic(magic)
      const spellResult = spell.interpret(textWithoutMagic.trim(), conf)
      const parsed = spellResult.getAllParsedDataWithTypes(spellPerMagic)

      return parsed
    })
  }

  this.getVisualizationCachedPackages = function () {
    return visualizationBundles
  }

  this.getVisualizationCachedPackageOrder = function () {
    return visualizationPackageOrder
  }

  /**
   * @returns {Promise} which returns bundleOrder and cache it in `visualizationPackageOrder`
   */
  this.getVisualizationPackageOrder = function () {
    return $http.get(baseUrlSrv.getRestApiBase() + '/helium/order/visualization')
      .then(function (response, status) {
        const order = response.data.body
        visualizationPackageOrder = order
        return order
      })
      .catch(function (error) {
        console.error('Can not get bundle order', error)
      })
  }

  this.setVisualizationPackageOrder = function (list) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/order/visualization', list)
  }

  this.enable = function (name, artifact) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/enable/' + name, artifact)
  }

  this.disable = function (name) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/disable/' + name)
  }

  this.saveConfig = function (pkg, defaultPackageConfig, closeConfigPanelCallback) {
    // in case of local package, it will include `/`
    const pkgArtifact = encodeURIComponent(pkg.artifact)
    const pkgName = pkg.name
    const filtered = createPersistableConfig(defaultPackageConfig)

    if (!pkgName || !pkgArtifact || !filtered) {
      console.error(
        `Can't save config for helium package '${pkgArtifact}'`, filtered)
      return
    }

    const url = `${baseUrlSrv.getRestApiBase()}/helium/config/${pkgName}/${pkgArtifact}`
    return $http.post(url, filtered)
      .then(() => {
        if (closeConfigPanelCallback) { closeConfigPanelCallback() }
      }).catch((error) => {
        console.error(`Failed to save config for ${pkgArtifact}`, error)
      })
  }

  /**
   * @returns {Promise<Object>} which including {name, Array<package info for artifact>}
   */
  this.getAllPackageInfo = function () {
    return $http.get(`${baseUrlSrv.getRestApiBase()}/helium/package`)
      .then(function (response, status) {
        return response.data.body
      })
      .catch(function (error) {
        console.error('Failed to get all package infos', error)
      })
  }

  this.getAllEnabledPackages = function () {
    return $http.get(`${baseUrlSrv.getRestApiBase()}/helium/enabledPackage`)
      .then(function (response, status) {
        return response.data.body
      })
      .catch(function (error) {
        console.error('Failed to get all enabled package infos', error)
      })
  }

  this.getSingleBundle = function (pkgName) {
    let url = `${baseUrlSrv.getRestApiBase()}/helium/bundle/load/${pkgName}`
    if (process.env.HELIUM_BUNDLE_DEV) {
      url = url + '?refresh=true'
    }

    return $http.get(url)
      .then(function (response, status) {
        const bundle = response.data
        if (bundle.substring(0, 'ERROR:'.length) === 'ERROR:') {
          console.error(`Failed to get bundle: ${pkgName}`, bundle)
          return '' // empty bundle will be filtered later
        }

        return bundle
      })
      .catch(function (error) {
        console.error(`Failed to get single bundle: ${pkgName}`, error)
      })
  }

  this.getDefaultPackages = function () {
    return this.getAllPackageInfo()
      .then(pkgSearchResults => {
        return createDefaultPackages(pkgSearchResults, $sce)
      })
  }

  this.getAllPackageInfoAndDefaultPackages = function () {
    return this.getAllPackageInfo()
      .then(pkgSearchResults => {
        return {
          pkgSearchResults: pkgSearchResults,
          defaultPackages: createDefaultPackages(pkgSearchResults, $sce),
        }
      })
  }

  /**
   * get all package configs.
   * @return { Promise<{name, Array<Object>}> }
   */
  this.getAllPackageConfigs = function () {
    const promisedDefaultPackages = this.getDefaultPackages()
    const promisedPersistedConfs =
      $http.get(`${baseUrlSrv.getRestApiBase()}/helium/config`)
      .then(function (response, status) {
        return response.data.body
      })

    return Promise.all([promisedDefaultPackages, promisedPersistedConfs])
      .then(values => {
        const defaultPackages = values[0]
        const persistedConfs = values[1]

        return createAllPackageConfigs(defaultPackages, persistedConfs)
      })
      .catch(function (error) {
        console.error('Failed to get all package configs', error)
      })
  }

  /**
   * get the package config which is persisted in server.
   * @return { Promise<Array<Object>> }
   */
  this.getSinglePackageConfigs = function (pkg) {
    const pkgName = pkg.name
    // in case of local package, it will include `/`
    const pkgArtifact = encodeURIComponent(pkg.artifact)

    if (!pkgName || !pkgArtifact) {
      console.error('Failed to fetch config for\n', pkg)
      return Promise.resolve([])
    }

    const confUrl = `${baseUrlSrv.getRestApiBase()}/helium/config/${pkgName}/${pkgArtifact}`
    const promisedConf = $http.get(confUrl)
      .then(function (response, status) {
        return response.data.body
      })

    return promisedConf.then(({confSpec, confPersisted}) => {
      const merged = mergePersistedConfWithSpec(confPersisted, confSpec)
      return merged
    })
  }

  this.getSinglePackageConfigUsingMagic = function (magic) {
    const pkgName = pkgNamePerMagic[magic]

    const confUrl = `${baseUrlSrv.getRestApiBase()}/helium/spell/config/${pkgName}`
    const promisedConf = $http.get(confUrl)
      .then(function (response, status) {
        return response.data.body
      })

    return promisedConf.then(({confSpec, confPersisted}) => {
      const merged = mergePersistedConfWithSpec(confPersisted, confSpec)
      return merged
    })
  }

  const p = this.getAllEnabledPackages()
    .then(enabledPackageSearchResults => {
      const promises = enabledPackageSearchResults.map(packageSearchResult => {
        const pkgName = packageSearchResult.pkg.name
        return this.getSingleBundle(pkgName)
      })

      return Promise.all(promises)
    })
    .then(bundles => {
      return bundles.reduce((acc, b) => {
        // filter out empty bundle
        if (b === '') { return acc }
        acc.push(b)
        return acc
      }, [])
    })

  // load should be promise
  this.load = p.then(availableBundles => {
    // evaluate bundles
    availableBundles.map(b => {
      // eslint-disable-next-line no-eval
      eval(b)
    })

    // extract bundles by type
    heliumBundles.map(b => {
      if (b.type === HeliumType.SPELL) {
        const spell = new b.class() // eslint-disable-line new-cap
        const pkgName = b.id
        spellPerMagic[spell.getMagic()] = spell
        pkgNamePerMagic[spell.getMagic()] = pkgName
      } else if (b.type === HeliumType.VISUALIZATION) {
        visualizationBundles.push(b)
      }
    })
  })

  this.init = function() {
    this.getVisualizationPackageOrder()
  }

  // init
  this.init()
}
