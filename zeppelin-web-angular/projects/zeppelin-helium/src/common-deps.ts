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

import * as common from '@angular/common';
import * as core from '@angular/core';
import * as forms from '@angular/forms';
import * as router from '@angular/router';
import * as rxjs from 'rxjs';

import * as dataSet from '@antv/data-set';
import * as g2 from '@antv/g2';
import * as sdk from '@zeppelin/sdk';
import * as visualization from '@zeppelin/visualization';
import * as lodash from 'lodash';

import * as ngZorro from 'ng-zorro-antd';
import * as tslib from 'tslib';
import * as zeppelinHelium from './public-api';

export const COMMON_DEPS = {
  '@angular/core': core,
  '@angular/common': common,
  '@angular/forms': forms,
  '@angular/router': router,
  '@antv/data-set': dataSet,
  '@antv/g2': g2,
  '@zeppelin/sdk': sdk,
  '@zeppelin/visualization': visualization,
  '@zeppelin/helium': zeppelinHelium,
  'lodash': lodash,
  'ng-zorro-antd': ngZorro,
  rxjs,
  tslib
};
