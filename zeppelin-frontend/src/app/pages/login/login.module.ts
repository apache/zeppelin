import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { NzButtonModule, NzFormModule, NzIconModule, NzInputModule } from 'ng-zorro-antd';

import { LoginRoutingModule } from './login-routing.module';
import { LoginComponent } from './login.component';

@NgModule({
  declarations: [LoginComponent],
  imports: [CommonModule, LoginRoutingModule, FormsModule, NzFormModule, NzInputModule, NzButtonModule, NzIconModule]
})
export class LoginModule {}
