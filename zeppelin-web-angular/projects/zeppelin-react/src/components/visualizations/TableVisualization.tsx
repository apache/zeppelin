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

import { useState, useEffect, useRef } from 'react';
import { Column, Line, Pie, Scatter } from '@antv/g2plot';
import { VisualizationControls } from './VisualizationControls';
import { parseTableData, TableData } from '@/utils';
import { useTableExport } from '@/hooks';
import type { ParagraphResult, ParagraphConfig, VisualizationMode } from '@/types';

interface TableVisualizationProps {
  result: ParagraphResult;
  config?: ParagraphConfig;
}

export const TableVisualization = ({ result, config }: TableVisualizationProps) => {
  const [currentMode, setCurrentMode] = useState<VisualizationMode>('table');
  const [tableData, setTableData] = useState<TableData | null>(null);
  const chartRef = useRef<HTMLDivElement>(null);
  const exportFile = useTableExport();

  const handleExport = (type: 'csv' | 'xlsx') => {
    if (tableData) {
      exportFile(tableData, type);
    }
  };

  useEffect(() => {
    if (config && config.graph && config.graph.mode) {
      setCurrentMode(config.graph.mode);
    }
    setTableData(parseTableData(result.data));
  }, [result, config]);

  const renderVisualization = () => {
    if (!tableData || tableData.rows.length === 0) return null;

    if (currentMode === 'table') {
      const columns = tableData.columnNames.map((col, idx) => ({
        title: col,
        dataIndex: idx,
        key: idx,
        render: (text: any) => text
      }));

      const dataSource = tableData.rows.map((row, idx) => ({
        key: idx,
        ...row.reduce((acc, cell, cellIdx) => ({ ...acc, [cellIdx]: cell }), {})
      }));

      const { Table } = require('antd');
      return (
        <Table
          columns={columns}
          dataSource={dataSource}
          size="small"
          scroll={{ x: true }}
          pagination={{ pageSize: 50 }}
        />
      );
    }

    return <div ref={chartRef} style={{ height: 400 }}></div>;
  };

  useEffect(() => {
    if (!chartRef.current || !tableData || tableData.rows.length === 0 || currentMode === 'table') return;

    const data = tableData.rows.map((row, idx) => ({
      category: row[0] || `Row ${idx + 1}`,
      value: parseFloat(row[1] || '0') || 0,
      x: idx,
      y: parseFloat(row[1] || '0') || 0
    }));

    let chart = null;

    switch (currentMode) {
      case 'multiBarChart':
        chart = new Column(chartRef.current, {
          data,
          xField: 'category',
          yField: 'value',
          color: '#1890ff',
          columnWidthRatio: 0.8,
        });
        break;
      case 'lineChart':
        chart = new Line(chartRef.current, {
          data,
          xField: 'category',
          yField: 'value',
          color: '#1890ff',
        });
        break;
      case 'pieChart':
        chart = new Pie(chartRef.current, {
          data,
          angleField: 'value',
          colorField: 'category',
        });
        break;
      case 'scatterChart':
        chart = new Scatter(chartRef.current, {
          data,
          xField: 'x',
          yField: 'y',
          color: '#1890ff',
        });
        break;
      case 'stackedAreaChart':
        chart = new Line(chartRef.current, {
          data,
          xField: 'category',
          yField: 'value',
          color: '#1890ff',
          point: {
            size: 3,
            shape: 'circle',
          },
          lineStyle: {
            lineWidth: 2,
          },
        });
        break;
    }

    if (chart) {
      chart.render();
    }

    return () => {
      if (chart) {
        chart.destroy();
      }
    };
  }, [currentMode, tableData]);

  return (
    <div>
      <VisualizationControls
        currentMode={currentMode}
        onModeChange={setCurrentMode}
        onExport={handleExport}
      />
      {renderVisualization()}
    </div>
  );
};
