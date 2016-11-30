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

var zeppelin = zeppelin || {};

/**
 * Create table data object from paragraph table type result
 */
zeppelin.TableData = function(columns, rows, comment) {
  this.columns = columns || [];
  this.rows = rows || [];
  this.comment = comment || '';
};

zeppelin.TableData.prototype.loadParagraphResult = function(paragraphResult) {
  if (!paragraphResult || paragraphResult.type !== 'TABLE') {
    console.log('Can not load paragraph result');
    return;
  }

  var columnNames = [];
  var rows = [];
  var array = [];
  var textRows = paragraphResult.msg.split('\n');
  var comment = '';
  var commentRow = false;

  for (var i = 0; i < textRows.length; i++) {
    var textRow = textRows[i];
    if (commentRow) {
      comment += textRow;
      continue;
    }

    if (textRow === '') {
      if (rows.length > 0) {
        commentRow = true;
      }
      continue;
    }
    var textCols = textRow.split('\t');
    var cols = [];
    var cols2 = [];
    for (var j = 0; j < textCols.length; j++) {
      var col = textCols[j];
      if (i === 0) {
        columnNames.push({name: col, index: j, aggr: 'sum'});
      } else {
        cols.push(col);
        cols2.push({key: (columnNames[i]) ? columnNames[i].name : undefined, value: col});
      }
    }
    if (i !== 0) {
      rows.push(cols);
      array.push(cols2);
    }
  }
  this.comment = comment;
  this.columns = columnNames;
  this.rows = rows;
};
