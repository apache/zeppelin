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

import { useCallback } from 'react';
import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
import type { TableData } from '@/utils';

const EXCEL_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8';
const EXCEL_EXTENSION = '.xlsx';

// React Hook version of Angular: src/app/visualizations/table/table-visualization.component.ts -> exportFile
export const useTableExport = () => {
  const exportFile = useCallback((tableData: TableData, type: 'csv' | 'xlsx') => {
    if (!tableData?.rows || tableData.rows.length === 0) {
      return;
    }

    const wb: {
      Sheets: { [key: string]: XLSX.WorkSheet };
      SheetNames: string[];
    } = {
      Sheets: {},
      SheetNames: []
    };

    const ws = XLSX.utils.json_to_sheet(tableData.rows);

    if (type === 'xlsx') {
      wb.Sheets['Sheet1'] = ws;
      wb.SheetNames.push('Sheet1');

      const excelBuffer = XLSX.write(wb, {
        bookType: 'xlsx',
        type: 'array'
      });

      const blob = new Blob([excelBuffer], { type: EXCEL_TYPE });
      saveAs(blob, `export${EXCEL_EXTENSION}`);
    } else {
      const separator = ',';
      const header = tableData.columnNames.join(separator);
      const rows = tableData.rows.map(row => row.join(separator));
      const content = [header, ...rows].join('\n');

      const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
      saveAs(blob, `export.${type}`);
    }
  }, []);

  return exportFile;
};
