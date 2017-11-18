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

// import globally uses css here
import 'github-markdown-css/github-markdown.css'

import './app/app.js'
import './app/app.controller.js'
import './app/home/home.controller.js'
import './app/notebook/notebook.controller.js'

import './app/tabledata/tabledata.js'
import './app/tabledata/transformation.js'
import './app/tabledata/pivot.js'
import './app/tabledata/passthrough.js'
import './app/tabledata/columnselector.js'
import './app/tabledata/advanced-transformation.js'
import './app/visualization/visualization.js'
import './app/visualization/builtins/visualization-table.js'
import './app/visualization/builtins/visualization-nvd3chart.js'
import './app/visualization/builtins/visualization-barchart.js'
import './app/visualization/builtins/visualization-piechart.js'
import './app/visualization/builtins/visualization-areachart.js'
import './app/visualization/builtins/visualization-linechart.js'
import './app/visualization/builtins/visualization-scatterchart.js'

import './app/jobmanager/jobmanager.component.js'
import './app/interpreter/interpreter.controller.js'
import './app/interpreter/interpreter.filter.js'
import './app/interpreter/interpreter-item.directive.js'
import './app/interpreter/widget/number-widget.directive.js'
import './app/credential/credential.controller.js'
import './app/configuration/configuration.controller.js'
import './app/notebook/revisions-comparator/revisions-comparator.component.js'
import './app/notebook/paragraph/paragraph.controller.js'
import './app/notebook/paragraph/clipboard.controller.js'
import './app/notebook/paragraph/resizable.directive.js'
import './app/notebook/paragraph/result/result.controller.js'
import './app/notebook/paragraph/code-editor/code-editor.directive.js'
import './app/notebook/save-as/save-as.service.js'
import './app/notebook/save-as/browser-detect.service.js'
import './app/notebook/elastic-input/elastic-input.controller.js'
import './app/notebook/dropdown-input/dropdown-input.directive.js'
import './app/notebook/note-var-share.service.js'
import './app/notebook-repository/notebook-repository.controller.js'
import './app/search/result-list.controller.js'
import './app/search/search.service.js'
import './app/helium'
import './app/helium/helium.service.js'
import './app/notebook/dynamic-forms/dynamic-forms.directive.js'
import './components/array-ordering/array-ordering.service.js'
import './components/navbar/navbar.controller.js'
import './components/navbar/expand-collapse/expand-collapse.directive.js'
import './components/note-create/note-create.controller.js'
import './components/note-create/visible.directive.js'
import './components/note-import/note-import.controller.js'
import './components/ng-enter/ng-enter.directive.js'
import './components/ng-escape/ng-escape.directive.js'
import './components/websocket/websocket-message.service.js'
import './components/websocket/websocket-event.factory.js'
import './components/note-list/note-list.factory.js'
import './components/base-url/base-url.service.js'
import './components/login/login.controller.js'
import './components/note-action/note-action.service.js'
import './components/note-rename/note-rename.controller.js'
import './components/note-rename/note-rename.service.js'
