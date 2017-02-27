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

import NetworkData from '../../../src/app/tabledata/networkdata.js';
import TableData from '../../../src/app/tabledata/tabledata.js';
import {DatasetType} from '../../../src/app/tabledata/dataset.js';
import DatasetFactory from '../../../src/app/tabledata/datasetfactory.js';


describe('DatasetFactory build', function() {
  var df;

  beforeAll(function() {
    df = new DatasetFactory();
  });

  it('should create a TableData instance', function() {
    var td = df.createDataset(DatasetType.TABLE);
    expect(td instanceof TableData).toBeTruthy();
    expect(td.columns.length).toBe(0);
    expect(td.rows.length).toBe(0);
  });

  it('should create a NetworkData instance', function() {
    var nd = df.createDataset(DatasetType.NETWORK);
    expect(nd instanceof NetworkData).toBeTruthy();
    expect(nd.columns.length).toBe(0);
    expect(nd.rows.length).toBe(0);
    expect(nd.graph).toEqual({});
  });

  it('should thrown an Error', function() {
    expect(function() { df.createDataset('text'); })
        .toThrow(new Error('Dataset type not found'));
  });

});
