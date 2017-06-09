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

import NetworkData from './networkdata.js'
import TableData from './tabledata.js'
import {DatasetType} from './dataset.js'
import DatasetFactory from './datasetfactory.js'

describe('DatasetFactory build', function() {
  let df

  beforeAll(function() {
    df = new DatasetFactory()
  })

  it('should create a TableData instance', function() {
    let td = df.createDataset(DatasetType.TABLE)
    expect(td instanceof TableData).toBeTruthy()
    expect(td.columns.length).toBe(0)
    expect(td.rows.length).toBe(0)
  })

  it('should create a NetworkData instance', function() {
    let nd = df.createDataset(DatasetType.NETWORK)
    expect(nd instanceof NetworkData).toBeTruthy()
    expect(nd.columns.length).toBe(0)
    expect(nd.rows.length).toBe(0)
    expect(nd.graph).toEqual({})
  })

  it('should thrown an Error', function() {
    expect(function() { df.createDataset('text') })
        .toThrow(new Error('Dataset type not found'))
  })
})
