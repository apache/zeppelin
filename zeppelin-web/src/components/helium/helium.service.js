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
  AbstractFrontendInterpreter,
  DefaultDisplayType
} from '../../app/frontend-interpreter'

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
    let frontendIntpWithMagic = {};
    let visualizationBundles = [];

    // load should be promise
    this.load = $http.get(url).success(function(response) {
      if (response.substring(0, 'ERROR:'.length) !== 'ERROR:') {
        // evaluate bundles
        eval(response);

        // extract bundles by type
        heliumBundles.map(b => {
          if (b.type === HeliumType.FRONTEND_INTERPRETER) {
            const interpreter = new b.class(); // eslint-disable-line new-cap
            frontendIntpWithMagic[interpreter.getMagic()] = interpreter;
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
     * @returns {FrontendInterpreterBase} undefined for non-available magic
     */
    this.getFrontendInterpreterUsingMagic = function(magic) {
      return frontendIntpWithMagic[magic];
    };

    /**
     * @returns {Object} map for `{ magic : interpreter }`
     */
    this.getAvailableFrontendInterpreters = function() {
      return frontendIntpWithMagic;
    };

    this.getVisualizationBundles = function() {
      return visualizationBundles;
    };

    this.getBundleOrder = function() {
      return $http.get(baseUrlSrv.getRestApiBase() + '/helium/bundleOrder');
    };

    this.setBundleOrder = function(list) {
      return $http.post(baseUrlSrv.getRestApiBase() + '/helium/bundleOrder', list);
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
