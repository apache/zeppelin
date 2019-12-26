import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { WorkspaceShareModule } from '../../workspace/share/share.module';
import { PublishedParagraphComponent } from './paragraph/paragraph.component';
import { PublishedRoutingModule } from './published-ruoting.module';

@NgModule({
  declarations: [PublishedParagraphComponent],
  imports: [CommonModule, WorkspaceShareModule, PublishedRoutingModule]
})
export class PublishedModule {}
