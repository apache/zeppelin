import { DragDropModule } from '@angular/cdk/drag-drop';
import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  NzButtonModule,
  NzCardModule,
  NzCheckboxModule,
  NzDropDownModule,
  NzFormModule,
  NzGridModule,
  NzIconModule,
  NzInputModule,
  NzMenuModule,
  NzRadioModule,
  NzTableModule,
  NzTagModule
} from 'ng-zorro-antd';

import { AreaChartVisualizationComponent } from './area-chart/area-chart-visualization.component';
import { BarChartVisualizationComponent } from './bar-chart/bar-chart-visualization.component';
import { VisualizationPivotSettingComponent } from './common/pivot-setting/pivot-setting.component';
import { VisualizationScatterSettingComponent } from './common/scatter-setting/scatter-setting.component';
import { VisualizationXAxisSettingComponent } from './common/x-axis-setting/x-axis-setting.component';
import { LineChartVisualizationComponent } from './line-chart/line-chart-visualization.component';
import { PieChartVisualizationComponent } from './pie-chart/pie-chart-visualization.component';
import { ScatterChartVisualizationComponent } from './scatter-chart/scatter-chart-visualization.component';
import { TableVisualizationComponent } from './table/table-visualization.component';

const VisualizationComponents = [
  TableVisualizationComponent,
  AreaChartVisualizationComponent,
  BarChartVisualizationComponent,
  LineChartVisualizationComponent,
  PieChartVisualizationComponent,
  ScatterChartVisualizationComponent
];

@NgModule({
  declarations: [
    ...VisualizationComponents,
    VisualizationPivotSettingComponent,
    VisualizationScatterSettingComponent,
    VisualizationXAxisSettingComponent
  ],
  entryComponents: [...VisualizationComponents],
  exports: [...VisualizationComponents],
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    NzTableModule,
    NzCardModule,
    NzTagModule,
    NzFormModule,
    NzInputModule,
    NzGridModule,
    NzIconModule,
    NzMenuModule,
    NzDropDownModule,
    NzRadioModule,
    NzCheckboxModule,
    NzButtonModule
  ]
})
export class VisualizationModule {}
