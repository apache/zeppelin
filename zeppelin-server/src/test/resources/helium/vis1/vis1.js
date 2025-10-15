/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import Visualization from 'zeppelin-vis'
import PassthroughTransformation from 'zeppelin-tabledata/passthrough'

/**
 * Base class for visualization
 */
export default class vis1 extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config)
    this.passthrough = new PassthroughTransformation(config);
    console.log('passthrough %o', this.passthrough);
  }

  render(tableData) {
    this.targetEl.html('Vis1')
  }

  getTransformation() {
    return this.passthrough
  }
}
