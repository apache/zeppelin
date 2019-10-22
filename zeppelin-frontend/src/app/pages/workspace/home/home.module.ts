import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { NzGridModule, NzIconModule, NzToolTipModule } from 'ng-zorro-antd';

import { ShareModule } from '@zeppelin/share';

import { HomeRoutingModule } from './home-routing.module';
import { HomeComponent } from './home.component';

@NgModule({
  declarations: [HomeComponent],
  imports: [CommonModule, HomeRoutingModule, NzGridModule, NzIconModule, NzToolTipModule, ShareModule]
})
export class HomeModule {}
