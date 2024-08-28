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

export default class Result {
  constructor(data) {
    this.data = data;
  }

  checkAndReplaceCarriageReturn() {
    const str = this.data.replace(/\r\n/g, '\n');
    if (/\r/.test(str)) {
      let newGenerated = '';
      let strArr = str.split('\n');
      for (let str of strArr) {
        if (/\r/.test(str)) {
          let splitCR = str.split('\r');
          newGenerated += splitCR[splitCR.length - 1] + '\n';
        } else {
          newGenerated += str + '\n';
        }
      }
      // remove last "\n" character
      return newGenerated.slice(0, -1);
    } else {
      return str;
    }
  }
}
