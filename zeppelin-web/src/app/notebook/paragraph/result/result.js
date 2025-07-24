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
      const generatedLines = str.split('\n').map((line) => {
        if (!/\r/.test(line)) {
          return line;
        }
        const parts = line.split('\r');
        let currentLine = parts[0];
        for (let i = 1; i < parts.length; i++) {
          const part = parts[i];
          const partLength = part.length;
          // apply terminal-like output. carriage return has the effect of moving output cursor to the front.
          const overwritten = part + currentLine.substring(partLength);
          currentLine = overwritten;
        }
        return currentLine;
      });
      return generatedLines.join('\n');
    } else {
      return str;
    }
  }
}
