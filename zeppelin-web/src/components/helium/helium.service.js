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

import { HeliumType, } from './helium-type';
import {
  createAllPackageConfigs,
  createSinglePackageConfig,
  createPersistableConfig,
} from './helium-conf';
import {
  createDefaultPackages,
  findPackageByVersion,
  findPackageByMagic,
} from './helium-package';

angular.module('zeppelinWebApp').service('heliumService', heliumService);

export default function heliumService($http, $sce, baseUrlSrv) {
  'ngInject';

  var url = baseUrlSrv.getRestApiBase() + '/helium/bundle/load';
  if (process.env.HELIUM_BUNDLE_DEV) {
    url = url + '?refresh=true';
  }

  // name `heliumBundles` should be same as `HelumBundleFactory.HELIUM_BUNDLES_VAR`
  var heliumBundles = [];
  // map for `{ magic: interpreter }`
  let spellPerMagic = {};
  let visualizationBundles = [];

  // load should be promise
  this.load = $http.get(url).success(function(response) {
    if (response.substring(0, 'ERROR:'.length) !== 'ERROR:') {
      // evaluate bundles
      eval(response);

      // extract bundles by type
      heliumBundles.map(b => {
        if (b.type === HeliumType.SPELL) {
          const spell = new b.class(); // eslint-disable-line new-cap
          spellPerMagic[spell.getMagic()] = spell;
        } else if (b.type === HeliumType.VISUALIZATION) {
          visualizationBundles.push(b);
        }
      });
    } else {
      console.error(response);
    }
  });

  /**
   * @param magic {string} e.g `%flowchart`
   * @returns {SpellBase} undefined if magic is not registered
   */
  this.getSpellByMagic = function(magic) {
    return spellPerMagic[magic];
  };

  /**
   */
  this.executeSpell = function(magic, textWithoutMagic) {
    const spell = this.getSpellByMagic(magic);
    const spellResult = spell.interpret(textWithoutMagic);
    const parsed = spellResult.getAllParsedDataWithTypes(
      spellPerMagic, magic, textWithoutMagic);

    return parsed;
  };

  this.executeSpellAsDisplaySystem = function(type, data) {
    const spell = this.getSpellByMagic(type);
    const spellResult = spell.interpret(data.trim());
    const parsed = spellResult.getAllParsedDataWithTypes(spellPerMagic);

    return parsed;
  };

  this.getVisualizationBundles = function() {
    return visualizationBundles;
  };

  /**
   * @returns {Promise} which returns bundleOrder
   */
  this.getVisualizationPackageOrder = function() {
    return $http.get(baseUrlSrv.getRestApiBase() + '/helium/order/visualization')
      .then(function(response, status) {
        return response.data.body;
      })
      .catch(function(error) {
        console.error('Can not get bundle order', error);
      });
  };

  this.setVisualizationPackageOrder = function(list) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/order/visualization', list);
  };

  this.enable = function(name, artifact) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/enable/' + name, artifact);
  };

  this.disable = function(name) {
    return $http.post(baseUrlSrv.getRestApiBase() + '/helium/disable/' + name);
  };

  this.saveConfig = function(pkgName, pkgVersion, defaultPackageConfig) {
    const filtered = createPersistableConfig(defaultPackageConfig);

    if (!pkgName || !pkgVersion || !filtered) {
      console.error(
        `Can't save helium package '${pkgName}@${pkgVersion}' config`, filtered);
      return;
    }

    const url = `${baseUrlSrv.getRestApiBase()}/helium/config/${pkgName}/${pkgVersion}`;
    return $http.post(url, filtered);
  };

  /**
   * @returns {Promise<Object>} which including {name, Array<package info for version>}
   */
  this.getAllPackageInfo = function() {
    return $http.get(`${baseUrlSrv.getRestApiBase()}/helium/package`)
      .then(function(response, status) {
        return response.data.body;
      })
      .catch(function(error) {
        console.error('Failed to get all package infos', error);
      });
  };

  /**
   * @returns {Promise<Array>} which including package info list for all versions
   */
  this.getSinglePackageInfo = function(pkgName, pkgVersion) {
    return $http.get(`${baseUrlSrv.getRestApiBase()}/helium/package/${pkgName}`)
      .then(response => {
        return response.data.body;
      })
      .then(singlePkgSearchResults => {
        const found = findPackageByVersion(singlePkgSearchResults, pkgVersion);

        if (!found) {
          throw new Error(`Could not find package info for '${pkgName}@${pkgVersion}'`);
        }

        return found;
      });
  };

  this.getDefaultPackages = function() {
    return this.getAllPackageInfo()
      .then(pkgSearchResults => {
        return createDefaultPackages(pkgSearchResults, $sce);
      });
  };

  this.getAllPackageInfoAndDefaultPackages = function() {
    return this.getAllPackageInfo()
      .then(pkgSearchResults => {
        return {
          pkgSearchResults: pkgSearchResults,
          defaultPackages: createDefaultPackages(pkgSearchResults, $sce),
        };
      });
  };

  /**
   * get all package configs.
   * @return Promise<{name, {version, {confKey, confVal}}}>
   */
  this.getAllPackageConfigs = function() {
    const promisedDefaultPackages = this.getDefaultPackages();
    const promisedPersistedConfs =
      $http.get(`${baseUrlSrv.getRestApiBase()}/helium/config`)
      .then(function(response, status) {
        return response.data.body;
      });

    return Promise.all([promisedDefaultPackages, promisedPersistedConfs])
      .then(values => {
        const defaultPackages = values[0];
        const persistedConfs = values[1];

        return createAllPackageConfigs(defaultPackages, persistedConfs);
      })
      .catch(function(error) {
        console.error('Failed to get all package configs', error);
      });
  };

  /**
   * get the package config which is persisted in server.
   * @return Promise<{confKey, confVal}>
   */
  this.getSinglePackageConfig = function(pkgName, pkgVersion) {
    const promisedPkgSearchResult = this.getSinglePackageInfo(pkgName, pkgVersion);

    const confUrl = `${baseUrlSrv.getRestApiBase()}/helium/config/${pkgName}/${pkgVersion}`;
    const promisedConf = $http.get(confUrl)
      .then(function(response, status) {
        return response.data.body;
      });

    return Promise.all([promisedPkgSearchResult, promisedConf])
      .then(values => {
        const pkgSearchResult = values[0];
        const persistedConf = values[1];

        const merged = createSinglePackageConfig(pkgSearchResult.pkg, persistedConf);
        return merged;
      })
      .catch(function(error) {
        console.error(`Failed to get package config for '${pkgName}@${pkgVersion}'`, error);
      });
  };

  this.getSinglePackageConfigUsingMagic = function(magic) {
    this.getDefaultPackages()
      .then(defaultPackages => {
        const pkgSearchResult = findPackageByMagic(defaultPackages, magic)
        const pkgVersion = pkgSearchResult.pkg.version;
        const pkgName = pkgSearchResult.pkg.name;

        return this.getSinglePackageConfig(pkgName, pkgVersion);
      })
  };
}
