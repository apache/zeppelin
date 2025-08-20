/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Injectable } from '@angular/core';
import { HeliumClassicTableData } from '@zeppelin/interfaces';
import { TableData } from '@zeppelin/visualization';

@Injectable({
  providedIn: 'root'
})
export class TableDataAdapterService {
  constructor() {}

  /**
   * Convert modern TableData to classic format expected by AngularJS visualizations
   */
  convertToClassicFormat(modernTableData: TableData): Omit<HeliumClassicTableData, 'loadParagraphResult' | 'refresh'> {
    const classicColumns: HeliumClassicTableData['columns'] = modernTableData.columns.map((columnName, index) => ({
      name: columnName,
      index,
      aggr: 'sum' // Default aggregation
    }));

    // Convert rows from modern format (objects) to classic format (arrays)
    const classicRows: string[][] = [];

    if (modernTableData.rows && modernTableData.rows.length > 0) {
      // Check if rows are objects (modern format) or arrays (already classic format)
      const firstRow = modernTableData.rows[0];

      if (Array.isArray(firstRow)) {
        // Already in classic format (array of arrays)
        for (const row of modernTableData.rows) {
          classicRows.push(row);
        }
      } else if (typeof firstRow === 'object' && firstRow !== null) {
        // Modern format (array of objects) - convert to classic format
        modernTableData.rows.forEach(rowObj => {
          const rowArray: string[] = [];
          modernTableData.columns.forEach(columnName => {
            rowArray.push(rowObj[columnName]);
          });
          classicRows.push(rowArray);
        });
      }
    }

    return {
      columns: classicColumns,
      rows: classicRows,
      comment: '' // Modern TableData doesn't have comment field
    };
  }

  /**
   * Create a classic TableData-like object with the required methods
   */
  createClassicTableDataProxy(modernTableData: TableData): HeliumClassicTableData {
    const classicData = this.convertToClassicFormat(modernTableData);

    // Create a proxy object that mimics the classic TableData interface
    const proxy: HeliumClassicTableData = {
      columns: classicData.columns,
      rows: classicData.rows,
      comment: classicData.comment,

      // Add any methods that classic visualizations might expect
      loadParagraphResult: paragraphResult => {
        // Delegate to modern TableData's method
        modernTableData.loadParagraphResult(paragraphResult);

        // Update proxy data after loading
        const updatedClassicData = this.convertToClassicFormat(modernTableData);
        proxy.columns = updatedClassicData.columns;
        proxy.rows = updatedClassicData.rows;
        proxy.comment = updatedClassicData.comment;
      },

      // Refresh data from modern TableData
      refresh: () => {
        const updatedClassicData = this.convertToClassicFormat(modernTableData);
        proxy.columns = updatedClassicData.columns;
        proxy.rows = updatedClassicData.rows;
        proxy.comment = updatedClassicData.comment;
      }
    };

    return proxy;
  }
}
