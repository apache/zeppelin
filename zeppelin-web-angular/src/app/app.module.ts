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

import { registerLocaleData } from '@angular/common';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import en from '@angular/common/locales/en';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Router, RouterModule } from '@angular/router';

import { ZeppelinHeliumModule } from '@zeppelin/helium';
import { en_US, NZ_I18N } from 'ng-zorro-antd/i18n';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzNotificationService } from 'ng-zorro-antd/notification';

import { MESSAGE_INTERCEPTOR, TRASH_FOLDER_ID_TOKEN } from '@zeppelin/interfaces';
import { loadMonacoBefore } from '@zeppelin/languages';
import { TicketService } from '@zeppelin/services';
import { ShareModule } from '@zeppelin/share';

import { NZ_CODE_EDITOR_CONFIG } from '@zeppelin/share/code-editor';
import { AppHttpInterceptor } from './app-http.interceptor';
import { AppMessageInterceptor } from './app-message.interceptor';
import { AppRoutingModule } from './app-routing.module';
import { RUNTIME_COMPILER_PROVIDERS } from './app-runtime-compiler.providers';
import { AppComponent } from './app.component';

export const loadMonaco = () => {
  loadMonacoBefore();
};

registerLocaleData(en);

@NgModule({
  declarations: [AppComponent],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    BrowserAnimationsModule,
    ShareModule,
    AppRoutingModule,
    RouterModule,
    ZeppelinHeliumModule
  ],
  providers: [
    ...RUNTIME_COMPILER_PROVIDERS,
    {
      provide: NZ_I18N,
      useValue: en_US
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AppHttpInterceptor,
      multi: true,
      deps: [TicketService]
    },
    {
      provide: NZ_CODE_EDITOR_CONFIG,
      useValue: {
        defaultEditorOption: {
          scrollBeyondLastLine: false
        },
        onLoad: loadMonaco
      }
    },
    {
      provide: MESSAGE_INTERCEPTOR,
      useClass: AppMessageInterceptor,
      deps: [Router, NzNotificationService, TicketService, NzModalService]
    },
    {
      provide: TRASH_FOLDER_ID_TOKEN,
      useValue: '~Trash'
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
