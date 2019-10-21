import { NgModule } from '@angular/core';
import { JsonVisComponent } from './json-vis.component';
import { CommonModule } from '@angular/common';

@NgModule({
  imports: [CommonModule],
  declarations: [JsonVisComponent],
  entryComponents: [JsonVisComponent],
  exports: [JsonVisComponent]
})
export class JsonVisModule { }
