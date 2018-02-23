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

/**
 * Base class for visualization.
 */
export default class Visualization {
  constructor(targetEl, config) {
    this.targetEl = targetEl;
    this.config = config;
    this._dirty = false;
    this._active = false;
    this._emitter = () => {};
  }

  /**
   * Get transformation.
   * @abstract
   * @return {Transformation}
   */
  getTransformation() {
    // override this
    throw new TypeError('Visualization.getTransformation() should be overrided');
  }

  /**
   * Method will be invoked when data or configuration changed.
   * @abstract
   */
  render(tableData) {
    // override this
    throw new TypeError('Visualization.render() should be overrided');
  }

  /**
   * Refresh visualization.
   */
  refresh() {
    // override this
    console.warn('A chart is missing refresh function, it might not work preperly');
  }

  /**
   * Method will be invoked when visualization need to be destroyed.
   * Don't need to destroy this.targetEl.
   */
  destroy() {
    // override this
  }

  /**
   * return {
   *   template : angular template string or url (url should end with .html),
   *   scope : an object to bind to template scope
   * }
   */
  getSetting() {
    // override this
  }

  /**
   * Activate. Invoked when visualization is selected.
   */
  activate() {
    if (!this._active || this._dirty) {
      this.refresh();
      this._dirty = false;
    }
    this._active = true;
  }

  /**
   * Deactivate. Invoked when visualization is de selected.
   */
  deactivate() {
    this._active = false;
  }

  /**
   * Is active.
   */
  isActive() {
    return this._active;
  }

  /**
   * When window or paragraph is resized.
   */
  resize() {
    if (this.isActive()) {
      this.refresh();
    } else {
      this._dirty = true;
    }
  }

  /**
   * Set new config.
   */
  setConfig(config) {
    this.config = config;
    if (this.isActive()) {
      this.refresh();
    } else {
      this._dirty = true;
    }
  }

  /**
   * Emit config. config will sent to server and saved.
   */
  emitConfig(config) {
    this._emitter(config);
  }

  /**
   * Render setting.
   */
  renderSetting(targetEl) {
    let setting = this.getSetting();
    if (!setting) {
      return;
    }

    // already readered
    if (this._scope) {
      let self = this;
      this._scope.$apply(function() {
        for (let k in setting.scope) {
          if (setting.scope.hasOwnProperty(k)) {
            self._scope[k] = setting.scope[k];
          }
        }

        for (let k in self._prevSettingScope) {
          if (!setting.scope[k]) {
            self._scope[k] = setting.scope[k];
          }
        }
      });
      return;
    } else {
      this._prevSettingScope = setting.scope;
    }

    let scope = this._createNewScope();
    for (let k in setting.scope) {
      if (setting.scope.hasOwnProperty(k)) {
        scope[k] = setting.scope[k];
      }
    }
    let template = setting.template;

    if (template.split('\n').length === 1 &&
        template.endsWith('.html')) { // template is url
      this._templateRequest(template).then((t) =>
      _renderSetting(this, targetEl, t, scope)
      );
    } else {
      _renderSetting(this, targetEl, template, scope);
    }
  }
}

function _renderSetting(instance, targetEl, template, scope) {
  instance._targetEl = targetEl;
  targetEl.html(template);
  instance._compile(targetEl.contents())(scope);
  instance._scope = scope;
}
