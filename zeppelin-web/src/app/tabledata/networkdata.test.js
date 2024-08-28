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

import NetworkData from './networkdata.js';
import {DatasetType} from './dataset.js';

describe('NetworkData build', function() {
  let nd;

  beforeEach(function() {
    nd = new NetworkData();
  });

  it('should initialize the default value', function() {
    expect(nd.columns.length).toBe(0);
    expect(nd.rows.length).toBe(0);
    expect(nd.graph).toEqual({});
  });

  it('should able to create NetowkData from paragraph result', function() {
    let jsonExpected = {nodes: [{id: 1}, {id: 2}], edges: [{source: 2, target: 1, id: 1}]};
    nd.loadParagraphResult({
      type: DatasetType.NETWORK,
      msg: JSON.stringify(jsonExpected),
    });

    expect(nd.columns.length).toBe(1);
    expect(nd.rows.length).toBe(3);
    expect(nd.graph.nodes[0].id).toBe(jsonExpected.nodes[0].id);
    expect(nd.graph.nodes[1].id).toBe(jsonExpected.nodes[1].id);
    expect(nd.graph.edges[0].id).toBe(jsonExpected.edges[0].id);
    expect(nd.graph.edges[0].source).toBe(jsonExpected.edges[0].source);
    expect(nd.graph.edges[0].target).toBe(jsonExpected.edges[0].target);
  });

  it('should able to show data fields source and target', function() {
    let jsonExpected = {nodes: [{id: 1, data: {source: 'Source'}}, {id: 2, data: {target: 'Target'}}],
      edges: [{source: 2, target: 1, id: 1, data: {source: 'Source Edge Data', target: 'Target Edge Data'}}]};
    nd.loadParagraphResult({
      type: DatasetType.NETWORK,
      msg: JSON.stringify(jsonExpected),
    });

    expect(nd.columns.length).toBe(3);
    expect(nd.rows.length).toBe(3);
    expect(nd.graph.nodes[0].id).toBe(jsonExpected.nodes[0].id);
    expect(nd.graph.nodes[1].id).toBe(jsonExpected.nodes[1].id);
    expect(nd.graph.edges[0].id).toBe(jsonExpected.edges[0].id);
    expect(nd.graph.edges[0].source).toBe(jsonExpected.edges[0].source);
    expect(nd.graph.edges[0].target).toBe(jsonExpected.edges[0].target);
    expect(nd.graph.nodes[0].data.source).toBe(jsonExpected.nodes[0].data.source);
    expect(nd.graph.nodes[1].data.target).toBe(jsonExpected.nodes[1].data.target);
    expect(nd.graph.edges[0].data.source).toBe(jsonExpected.edges[0].data.source);
    expect(nd.graph.edges[0].data.target).toBe(jsonExpected.edges[0].data.target);
  });
});
