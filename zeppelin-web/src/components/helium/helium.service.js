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

(function() {
  angular.module('zeppelinWebApp').service('heliumService', heliumService);

  heliumService.$inject = ['$http', 'baseUrlSrv', 'ngToast'];

  function heliumService($http, baseUrlSrv, ngToast) {

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
     * @returns {Object} map for `{ magic : spell }`
     */
    this.getAllSpells = function() {
      return spellPerMagic;
    };

    this.getVisualizationBundles = function() {
      return visualizationBundles;
    };

    this.getVisualizationPackageOrder = function() {
      return $http.get(baseUrlSrv.getRestApiBase() + '/helium/order/visualization');
    };

    this.setVisualizationPackageOrder = function(list) {
      return $http.post(baseUrlSrv.getRestApiBase() + '/helium/order/visualization', list);
    };

    this.getAllPackageInfo = function() {
      return $http.get(baseUrlSrv.getRestApiBase() + '/helium/all');
    };

    this.enable = function(name, artifact) {
      return $http.post(baseUrlSrv.getRestApiBase() + '/helium/enable/' + name, artifact);
    };

    this.disable = function(name) {
      return $http.post(baseUrlSrv.getRestApiBase() + '/helium/disable/' + name);
    };
  }
})();
