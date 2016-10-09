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

var zeppelin = zeppelin || {};

/**
 * Base class for visualization
 */
zeppelin.Visualization = function(targetEl, config) {
  this.targetEl = targetEl;
  this.config = config;
};

/**
 * get transformation
 */
zeppelin.Visualization.prototype.getTransformation = function() {
  // override this
};

/**
 * Method will be invoked when data or configuration changed
 */
zeppelin.Visualization.prototype.render = function(tableData) {
  // override this
};

/**
 * Set new config
 */
zeppelin.Visualization.prototype.setConfig = function(config) {
  this.config = config;
};

/**
 * method will be invoked when visualization need to be destroyed.
 * Don't need to destroy this.targetEl.
 */
zeppelin.Visualization.prototype.destroy = function() {
  // override this
};
