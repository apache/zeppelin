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

import { Button, Space } from 'antd';
import { BarChartOutlined, PieChartOutlined, LineChartOutlined, DotChartOutlined, TableOutlined, AreaChartOutlined, DownOutlined, DownloadOutlined, FileExcelOutlined } from '@ant-design/icons';

type VisualizationMode = 'table' | 'multiBarChart' | 'pieChart' | 'lineChart' | 'stackedAreaChart' | 'scatterChart';

interface VisualizationControlsProps {
  currentMode: VisualizationMode;
  onModeChange: (mode: VisualizationMode) => void;
  onExport: (type: 'csv' | 'xlsx') => void;
}

export const VisualizationControls = ({
  currentMode,
  onModeChange,
  onExport
}: VisualizationControlsProps) => {
  const visualizations = [
    { id: 'table', name: 'Table', icon: <TableOutlined /> },
    { id: 'multiBarChart', name: 'Bar Chart', icon: <BarChartOutlined /> },
    { id: 'pieChart', name: 'Pie Chart', icon: <PieChartOutlined /> },
    { id: 'lineChart', name: 'Line Chart', icon: <LineChartOutlined /> },
    { id: 'stackedAreaChart', name: 'Area Chart', icon: <AreaChartOutlined /> },
    { id: 'scatterChart', name: 'Scatter Chart', icon: <DotChartOutlined /> }
  ] as const;

  return (
    <div>
      <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space.Compact>
          {visualizations.map(viz => (
            <Button
              key={viz.id}
              type={currentMode === viz.id ? 'primary' : 'default'}
              icon={viz.icon}
              onClick={() => onModeChange(viz.id as VisualizationMode)}
              size="small"
            >
              {viz.name}
            </Button>
          ))}
        </Space.Compact>
        <Space.Compact>
          <Button
            icon={<DownloadOutlined />}
            size="small"
            onClick={() => onExport('csv')}
          >
            Export CSV
          </Button>
          <Button
            icon={<FileExcelOutlined />}
            size="small"
            onClick={() => onExport('xlsx')}
          >
            Export Excel
          </Button>
        </Space.Compact>
      </Space>

    </div>
  );
};
