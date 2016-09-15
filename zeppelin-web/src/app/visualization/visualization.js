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

var zeppelin = {};
/**
 * Base class for visualization
 */
zeppelin.Visualization = function(targetEl) {
  this.targetEl = targetEl;
};

/**
 * Method will be invoked
 */
zeppelin.Visualization.prototype.render = function(tableData) {
  // override this
};

/**
 * Invoked when container is resized
 */
zeppelin.Visualization.prototype.onResize = function() {
  // override this
};

/**
 * method will be invoked when visualization need to be destroyed.
 * Don't need to destroy this.targetEl.
 */
zeppelin.Visualization.prototype.destroy = function() {
  // override this
};

zeppelin.Visualization.prototype.setHeight = function(height) {
  this.targetEl.height(height);
  this.onResize();
};
