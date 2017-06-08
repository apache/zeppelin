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

import Transformation from './transformation'

/**
 * trasformation settings for network visualization
 */
export default class NetworkTransformation extends Transformation {
  getSetting() {
    let self = this
    let configObj = self.config
    return {
      template: 'app/tabledata/network_settings.html',
      scope: {
        config: configObj,
        isEmptyObject: function(obj) {
          obj = obj || {}
          return angular.equals(obj, {})
        },
        setNetworkLabel: function(label, value) {
          configObj.properties[label].selected = value
        },
        saveConfig: function() {
          self.emitConfig(configObj)
        }
      }
    }
  }

  setConfig(config) {
  }

  transform(networkData) {
    return networkData
  }
}
