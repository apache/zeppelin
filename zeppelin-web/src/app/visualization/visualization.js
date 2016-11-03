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
  this._resized = false;
  this._active = false;
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
 * Refresh visualization.
 */
zeppelin.Visualization.prototype.refresh = function() {
  // override this
};

/**
 * Activate. invoked when visualization is selected
 */
zeppelin.Visualization.prototype.activate = function() {
  console.log('active');
  if (!this._active && this._resized) {
    var self = this;
    // give some time for element ready
    setTimeout(function() {self.refresh();}, 300);
    this._resized = false;
  }
  this._active = true;
};

/**
 * Activate. invoked when visualization is de selected
 */
zeppelin.Visualization.prototype.deactivate = function() {
  console.log('deactive');
  this._active = false;
};

/**
 * Is active
 */
zeppelin.Visualization.prototype.isActive = function() {
  return this._active;
};

/**
 * When window or paragraph is resized
 */
zeppelin.Visualization.prototype.resize = function() {
  if (this.isActive()) {
    this.refresh();
  } else {
    this._resized = true;
  }
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
