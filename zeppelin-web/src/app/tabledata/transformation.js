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
zeppelin.Transformation = function(config) {
  this.config = config;
  this._emitter;
};

/**
 * return {
 *   template : angular template string or url (url should end with .html),
 *   scope : an object to bind to template scope
 * }
 */
zeppelin.Transformation.prototype.getSetting = function() {
  // override this
};

/**
 * Method will be invoked when tableData or config changes
 */
zeppelin.Transformation.prototype.transform = function(tableData) {
  // override this
};

/**
 * render setting
 */
zeppelin.Transformation.prototype.renderSetting = function(targetEl) {
  var setting = this.getSetting();
  if (!setting) {
    return;
  }

  this.settingScope = this._createNewScope();
  for (var k in setting.scope) {
    this.settingScope[k] = setting.scope[k];
  }
  var template = setting.template;
  var scope = this.settingScope;

  if (template.split('\n').length === 1 &&
      template.endsWith('.html')) { // template is url
    var self = this;
    this._templateRequest(template).then(function(t) {
      self._render(targetEl, t, scope);
    });
  } else {
    this._render(targetEl, template, scope);
  }
};

zeppelin.Transformation.prototype._render = function(targetEl, template, scope) {
  this._targetEl = targetEl;
  targetEl.html(template);
  this._compile(targetEl.contents())(scope);
};

zeppelin.Transformation.prototype.setConfig = function(config) {
  this.config = config;
};

/**
 * Emit config. config will sent to server and saved.
 */
zeppelin.Transformation.prototype.emitConfig = function(config) {
  this._emitter(config);
};
