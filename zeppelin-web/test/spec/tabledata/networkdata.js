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

describe('NetworkData build', function() {
  var nd;

  beforeEach(function() {
    var NetworkData = zeppelin.NetworkData;
    nd = new NetworkData();
  });

  it('should initialize the default value', function() {
    expect(nd.columns.length).toBe(0);
    expect(nd.rows.length).toBe(0);
    expect(nd.graph).toBe({});
  });

  it('should able to create NetowkData from paragraph result', function() {
    td.loadParagraphResult({
      type: zeppelin.DatasetTypes.NETWORK,
      msg: '{"nodes" : [{"id" : 1}, {"id" : 2}], "edges" : [{"source" : 2, "target" : 1, "id" : 1 }]}'
    });

    expect(td.columns.length).toBe(1);
    expect(td.rows.length).toBe(3);
    expect(td.graph).toBe({"nodes" : [{"id" : 1}, {"id" : 2}], "edges" : [{"source" : 2, "target" : 1, "id" : 1 }]});
  });
});
