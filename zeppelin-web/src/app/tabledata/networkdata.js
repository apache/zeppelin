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

import TableData from './tabledata'
import {DatasetType} from './dataset'

/**
 * Create network data object from paragraph graph type result
 */
export default class NetworkData extends TableData {
  constructor(graph) {
    super()
    this.graph = graph || {}
    if (this.graph.nodes) {
      this.loadParagraphResult({msg: JSON.stringify(graph), type: DatasetType.NETWORK})
    }
  }

  loadParagraphResult(paragraphResult) {
    if (!paragraphResult || paragraphResult.type !== DatasetType.NETWORK) {
      console.log('Can not load paragraph result')
      return
    }

    this.graph = JSON.parse(paragraphResult.msg.trim() || '{}')

    if (!this.graph.nodes) {
      console.log('Graph result is empty')
      return
    }

    this.graph.edges = this.graph.edges || []
    this.networkNodes = angular.equals({}, this.graph.labels || {})
            ? null : {count: this.graph.nodes.length, labels: this.graph.labels}
    this.networkRelationships = angular.equals([], this.graph.types || [])
            ? null : {count: this.graph.edges.length, types: this.graph.types}

    const rows = []
    const comment = ''
    const entities = this.graph.nodes.concat(this.graph.edges)
    const baseColumnNames = [{name: 'id', index: 0, aggr: 'sum'}]
    const containsLabelField = _.find(entities, (entity) => 'label' in entity) != null
    if (this.graph.labels || this.graph.types || containsLabelField) {
      baseColumnNames.push({name: 'label', index: 1, aggr: 'sum'})
    }
    const internalFieldsToJump = ['count', 'size', 'totalCount',
      'data', 'x', 'y', 'labels', 'source', 'target']
    const baseCols = _.map(baseColumnNames, (col) => col.name)
    let keys = _.map(entities, (elem) => Object.keys(elem.data || {}))
    keys = _.flatten(keys)
    keys = _.uniq(keys).filter((key) => baseCols.indexOf(key) === -1)
    const entityColumnNames = _.map(keys, (elem, i) => {
      return {name: elem, index: i + baseColumnNames.length, aggr: 'sum'}
    })
    const columnNames = baseColumnNames.concat(entityColumnNames)
    for (let i = 0; i < entities.length; i++) {
      const entity = entities[i]
      const col = []
      entity.data = entity.data || {}
      for (let j = 0; j < columnNames.length; j++) {
        const name = columnNames[j].name
        const value = name in entity && internalFieldsToJump.indexOf(name) === -1
            ? entity[name] : entity.data[name]
        const parsedValue = value === null || value === undefined ? '' : value
        col.push(parsedValue)
      }
      rows.push(col)
    }

    this.comment = comment
    this.columns = columnNames
    this.rows = rows
  }
}
