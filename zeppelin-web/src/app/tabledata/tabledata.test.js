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

import TableData from './tabledata.js'
import PivotTransformation from './pivot.js'

describe('TableData build', function () {
  let td

  beforeEach(function () {
    console.log(TableData)
    td = new TableData()
  })

  it('should initialize the default value', function () {
    expect(td.columns.length).toBe(0)
    expect(td.rows.length).toBe(0)
    expect(td.comment).toBe('')
  })

  it('should able to create Tabledata from paragraph result', function () {
    td.loadParagraphResult({
      type: 'TABLE',
      msg: 'key\tvalue\na\t10\nb\t20\n\nhello'
    })

    expect(td.columns.length).toBe(2)
    expect(td.rows.length).toBe(2)
    expect(td.comment).toBe('hello')
  })
})

describe('PivotTransformation build', function() {
  let pt

  beforeEach(function () {
    console.log(PivotTransformation)
    pt = new PivotTransformation()
  })

  it('check the result of keys, groups and values unique', function() {
    // set inited mock data
    let config = {
      common: {
        pivot: {
          keys: [{index: 4, name: '4'},
                 {index: 3, name: '3'},
                 {index: 4, name: '4'},
                 {index: 3, name: '3'},
                 {index: 3, name: '3'},
                 {index: 3, name: '3'},
                 {index: 3, name: '3'},
                 {index: 5, name: '5'}],
          groups: [],
          values: []
        }
      }
    }
    pt.tableDataColumns = [
        {index: 1, name: '1'},
        {index: 2, name: '2'},
        {index: 3, name: '3'},
        {index: 4, name: '4'},
        {index: 5, name: '5'}]

    pt.setConfig(config)

    pt.removeUnknown()

    expect(config.common.pivot.keys.length).toBe(3)
    expect(config.common.pivot.keys[0].index).toBe(4)
    expect(config.common.pivot.keys[1].index).toBe(3)
    expect(config.common.pivot.keys[2].index).toBe(5)
  })

  it('should aggregate values correctly', function() {
    let td = new TableData()
    td.loadParagraphResult({
      type: 'TABLE',
      msg: 'key\tvalue\na\t10\na\tnull\na\t0\na\t1\n'
    })

    let config = {
      common: {
        pivot: {
          keys: [
            {
              'name': 'key',
              'index': 0.0,
            }
          ],
          groups: [],
          values: [
            {
              'name': 'value',
              'index': 1.0,
              'aggr': 'sum'
            }
          ]
        }
      }
    }

    pt.setConfig(config)
    let transformed = pt.transform(td)
    expect(transformed.rows['a']['value(sum)'].value).toBe(11)

    pt.config.common.pivot.values[0].aggr = 'max'
    transformed = pt.transform(td)
    expect(transformed.rows['a']['value(max)'].value).toBe(10)

    pt.config.common.pivot.values[0].aggr = 'min'
    transformed = pt.transform(td)
    expect(transformed.rows['a']['value(min)'].value).toBe(0)

    pt.config.common.pivot.values[0].aggr = 'count'
    transformed = pt.transform(td)
    expect(transformed.rows['a']['value(count)'].value).toBe(4)
  })
})
