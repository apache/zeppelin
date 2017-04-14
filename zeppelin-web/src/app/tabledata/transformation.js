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
 * Base class for visualization
 */
export default class Transformation {
  constructor (config) {
    this.config = config
    this._emitter = () => {}
  }

  /**
   * return {
   *   template : angular template string or url (url should end with .html),
   *   scope : an object to bind to template scope
   * }
   */
  getSetting () {
    // override this
  }

  /**
   * Method will be invoked when tableData or config changes
   */
  transform (tableData) {
    // override this
  }

  /**
   * render setting
   */
  renderSetting (targetEl) {
    let setting = this.getSetting()
    if (!setting) {
      return
    }

    // already readered
    if (this._scope) {
      let self = this
      this._scope.$apply(function () {
        for (let k in setting.scope) {
          self._scope[k] = setting.scope[k]
        }

        for (let k in self._prevSettingScope) {
          if (!setting.scope[k]) {
            self._scope[k] = setting.scope[k]
          }
        }
      })
      return
    } else {
      this._prevSettingScope = setting.scope
    }

    let scope = this._createNewScope()
    for (let k in setting.scope) {
      scope[k] = setting.scope[k]
    }
    let template = setting.template

    if (template.split('\n').length === 1 &&
        template.endsWith('.html')) { // template is url
      let self = this
      this._templateRequest(template).then(function (t) {
        self._render(targetEl, t, scope)
      })
    } else {
      this._render(targetEl, template, scope)
    }
  }

  _render (targetEl, template, scope) {
    this._targetEl = targetEl
    targetEl.html(template)
    this._compile(targetEl.contents())(scope)
    this._scope = scope
  }

  setConfig (config) {
    this.config = config
  }

  /**
   * Emit config. config will sent to server and saved.
   */
  emitConfig (config) {
    this._emitter(config)
  }
}
